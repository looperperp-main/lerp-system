package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.api.dto.DocumentoFiscalRequestDTO;
import com.l.erp.emissaofiscalservice.domain.CertificadoDigital;
import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.CertificadoDigitalRepository;
import com.l.erp.emissaofiscalservice.repository.DocumentoFiscalRepository;
import com.l.erp.emissaofiscalservice.services.assinatura.XmlSignatureService;
import com.l.erp.emissaofiscalservice.services.certificado.CertificadoDigitalService;
import com.l.erp.emissaofiscalservice.services.crypto.EnvelopeEncryptionService;
import com.l.erp.emissaofiscalservice.services.nfe.ChaveAcesso;
import com.l.erp.emissaofiscalservice.services.nfe.NfeXmlBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import tools.jackson.databind.ObjectMapper;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;

/**
 * Passo assíncrono {@code RASCUNHO → ASSINADO} — spec §3 item 10: monta o XML, assina e grava
 * {@code chave_acesso} + {@code xml_assinado} <b>antes de qualquer transmissão</b>, na mesma transação
 * que muda o estado (se o processo cair depois, a nota ainda é consultável pela chave).
 *
 * <p>Roda dentro do job, sem requisição: não lê tenant de header nem de {@code SecurityContext}. O
 * {@code tenantId} vem do próprio documento (gravado no {@code POST}), porque este serviço é vendável
 * separadamente e quem identifica o dono do documento é a borda de autenticação, não este passo.</p>
 */
@Service
public class AssinaturaDocumentoService {

    /** Fuso do emitente por UF — {@code dhEmi} exige o offset da UF. Fernando de Noronha fica de fora. */
    private static final Map<String, String> FUSO_POR_UF = Map.of(
            "AC", "America/Rio_Branco",
            "AM", "America/Manaus",
            "RO", "America/Porto_Velho",
            "RR", "America/Boa_Vista",
            "MT", "America/Cuiaba",
            "MS", "America/Campo_Grande");
    private static final String FUSO_PADRAO = "America/Sao_Paulo";

    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final CertificadoDigitalRepository certificadoDigitalRepository;
    private final CertificadoDigitalService certificadoDigitalService;
    private final EnvelopeEncryptionService envelopeEncryptionService;
    private final DocumentoFiscalService documentoFiscalService;
    private final NfeXmlBuilder nfeXmlBuilder;
    private final XmlSignatureService xmlSignatureService;
    private final ObjectMapper objectMapper;

    public AssinaturaDocumentoService(DocumentoFiscalRepository documentoFiscalRepository,
                                      CertificadoDigitalRepository certificadoDigitalRepository,
                                      CertificadoDigitalService certificadoDigitalService,
                                      EnvelopeEncryptionService envelopeEncryptionService,
                                      DocumentoFiscalService documentoFiscalService,
                                      NfeXmlBuilder nfeXmlBuilder,
                                      XmlSignatureService xmlSignatureService,
                                      ObjectMapper objectMapper) {
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.certificadoDigitalRepository = certificadoDigitalRepository;
        this.certificadoDigitalService = certificadoDigitalService;
        this.envelopeEncryptionService = envelopeEncryptionService;
        this.documentoFiscalService = documentoFiscalService;
        this.nfeXmlBuilder = nfeXmlBuilder;
        this.xmlSignatureService = xmlSignatureService;
        this.objectMapper = objectMapper;
    }

    /**
     * @return {@code false} se o documento não está mais em {@code RASCUNHO} ou outra instância já o
     * está processando (nenhum dos dois é erro — o job só segue pro próximo).
     */
    @Transactional
    public boolean assinarSeRascunho(UUID documentoId) {
        var travado = documentoFiscalRepository.buscarPorIdEStatusComLock(documentoId, StatusDocumentoFiscal.RASCUNHO);
        if (travado.isEmpty()) {
            return false;
        }
        DocumentoFiscal documento = travado.get();
        DocumentoFiscalRequestDTO request =
                objectMapper.readValue(documento.getPayloadRecebido(), DocumentoFiscalRequestDTO.class);

        CertificadoDigital certificado = certificadoDigitalRepository
                .findByTenantIdAndEmitenteId(documento.getTenantId(), documento.getEmitenteId())
                .filter(CertificadoDigital::isAtivo)
                .orElseThrow(() -> new BusinessException(
                        Constants.EMISSAO_ERRO_CERTIFICADO_AUSENTE, HttpStatus.UNPROCESSABLE_ENTITY));
        if (certificado.getCertificadoValidoAte().isBefore(OffsetDateTime.now())) {
            throw new BusinessException(Constants.EMISSAO_ERRO_CERTIFICADO_VENCIDO, HttpStatus.UNPROCESSABLE_ENTITY);
        }

        certificadoDigitalService.exigirPosse(certificado, documento.getTenantId(), documento.getEmitenteId());

        char[] senha = new String(certificadoDigitalService.decifrar(certificado, certificado.getSenhaCifrada()),
                StandardCharsets.UTF_8).toCharArray();
        try {
            KeyStore keyStore = certificadoDigitalService.abrirKeyStore(certificado, senha);
            String alias = aliasDaChavePrivada(keyStore);
            PrivateKey chavePrivada = (PrivateKey) keyStore.getKey(alias, senha);
            X509Certificate x509 = (X509Certificate) keyStore.getCertificate(alias);

            String uf = request.emitente().endereco().uf();
            OffsetDateTime emissao = OffsetDateTime.now(ZoneId.of(FUSO_POR_UF.getOrDefault(uf, FUSO_PADRAO)));
            ChaveAcesso.Resultado chave = ChaveAcesso.gerar(uf, YearMonth.from(emissao), request.emitente().cnpj(),
                    Constants.NFE_MODELO_55, documento.getSerie(), documento.getNumero(), documento.getTpEmis(),
                    ChaveAcesso.gerarCodigoNumerico(documento.getNumero()));

            String xml = nfeXmlBuilder.montar(request, documento.getNumero(), chave, emissao);
            Document assinado = xmlSignatureService.assinar(parse(xml), "NFe" + chave.chave(), chavePrivada, x509);

            documento.setChaveAcesso(chave.chave());
            documento.setXmlAssinado(serializar(assinado));
            documentoFiscalService.aplicarTransicao(documento, StatusDocumentoFiscal.ASSINADO);
            return true;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao assinar o documento " + documentoId + ": " + e.getMessage(), e);
        } finally {
            Arrays.fill(senha, '\0');
        }
    }

    private static String aliasDaChavePrivada(KeyStore keyStore) throws Exception {
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) {
                return alias;
            }
        }
        throw new IllegalStateException("O certificado A1 não contém chave privada.");
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    /** Sem declaração XML e sem indentação: o leiaute proíbe formatação entre tags e a assinatura depende do texto. */
    private static String serializar(Document documento) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter saida = new StringWriter();
        transformer.transform(new DOMSource(documento), new StreamResult(saida));
        return saida.toString();
    }
}
