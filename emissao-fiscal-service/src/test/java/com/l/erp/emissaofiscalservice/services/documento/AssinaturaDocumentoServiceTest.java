package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.emissaofiscalservice.api.dto.DestinatarioDTO;
import com.l.erp.emissaofiscalservice.api.dto.DocumentoFiscalRequestDTO;
import com.l.erp.emissaofiscalservice.api.dto.EmitenteDTO;
import com.l.erp.emissaofiscalservice.api.dto.EnderecoDTO;
import com.l.erp.emissaofiscalservice.api.dto.ItemDocumentoDTO;
import com.l.erp.emissaofiscalservice.api.dto.SnapshotFiscalItemDTO;
import com.l.erp.emissaofiscalservice.domain.CertificadoDigital;
import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.CertificadoDigitalRepository;
import com.l.erp.emissaofiscalservice.repository.DocumentoFiscalRepository;
import com.l.erp.emissaofiscalservice.services.assinatura.XmlSignatureService;
import com.l.erp.emissaofiscalservice.services.certificado.CertificadoDigitalService;
import com.l.erp.emissaofiscalservice.services.crypto.EnvelopeEncryptionService;
import com.l.erp.emissaofiscalservice.services.nfe.NfeXmlBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import tools.jackson.databind.json.JsonMapper;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.SchemaFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Passo RASCUNHO → ASSINADO (spec §3 item 10): grava chave + XML assinado e transiciona na mesma
 * transação. Usa o certificado autoassinado de teste; o que se prova aqui é a mecânica (lock, estado,
 * persistência, assinatura íntegra depois de serializar), não a cadeia ICP-Brasil.
 */
@ExtendWith(MockitoExtension.class)
class AssinaturaDocumentoServiceTest {

    private static final char[] SENHA = "teste123".toCharArray();
    private static final Long TENANT_ID = 7L;
    private static final UUID EMITENTE_ID = UUID.randomUUID();
    private static final String CNPJ = "11222333000181";
    private static final BigDecimal CEM = new BigDecimal("100.00");

    @Mock
    private DocumentoFiscalRepository documentoRepository;
    @Mock
    private CertificadoDigitalRepository certificadoRepository;
    @Mock
    private CertificadoDigitalService certificadoService;
    @Mock
    private EnvelopeEncryptionService envelope;
    @Mock
    private DocumentoFiscalService documentoService;

    private AssinaturaDocumentoService service;
    private final JsonMapper json = JsonMapper.builder().build();
    private X509Certificate x509;

    @BeforeEach
    void setUp() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = getClass().getResourceAsStream("/certificado-teste.p12")) {
            keyStore.load(is, SENHA);
        }
        x509 = (X509Certificate) keyStore.getCertificate(keyStore.aliases().nextElement());

        service = new AssinaturaDocumentoService(documentoRepository, certificadoRepository, certificadoService,
                envelope, documentoService, new NfeXmlBuilder(), new XmlSignatureService(), json);

        lenient().when(certificadoService.abrirKeyStore(any(), any())).thenReturn(keyStore);
        lenient().when(certificadoService.decifrar(any(), any())).thenReturn("teste123".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void assinaGravaChaveEXmlETransicionaParaAssinado() {
        DocumentoFiscal documento = rascunho();
        when(documentoRepository.buscarPorIdEStatusComLock(any(), eq(StatusDocumentoFiscal.RASCUNHO)))
                .thenReturn(Optional.of(documento));
        when(certificadoRepository.findByTenantIdAndEmitenteId(TENANT_ID, EMITENTE_ID))
                .thenReturn(Optional.of(certificado(OffsetDateTime.now().plusDays(90), true)));

        assertTrue(service.assinarSeRascunho(UUID.randomUUID()));

        assertEquals(44, documento.getChaveAcesso().length());
        assertTrue(documento.getChaveAcesso().startsWith("33"), "cUF do RJ");
        assertTrue(documento.getXmlAssinado().contains("<Signature"));
        assertFalse(documento.getXmlAssinado().startsWith("<?xml"), "o leiaute não aceita declaração XML");
        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.ASSINADO);
    }

    @Test
    void xmlPersistidoContinuaValidoNoXsdEComAssinaturaIntegra() throws Exception {
        DocumentoFiscal documento = rascunho();
        when(documentoRepository.buscarPorIdEStatusComLock(any(), eq(StatusDocumentoFiscal.RASCUNHO)))
                .thenReturn(Optional.of(documento));
        when(certificadoRepository.findByTenantIdAndEmitenteId(TENANT_ID, EMITENTE_ID))
                .thenReturn(Optional.of(certificado(OffsetDateTime.now().plusDays(90), true)));

        service.assinarSeRascunho(UUID.randomUUID());

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document persistido = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(documento.getXmlAssinado())));

        var schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(getClass().getResource("/xsd/nfe/nfe_v4.00.xsd"));
        assertDoesNotThrow(() -> schema.newValidator().validate(new DOMSource(persistido)));

        var assinatura = persistido.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").item(0);
        var infNFe = persistido.getElementsByTagName("infNFe").item(0);
        DOMValidateContext contexto = new DOMValidateContext(x509.getPublicKey(), assinatura);
        contexto.setIdAttributeNS((org.w3c.dom.Element) infNFe, null, "Id");
        // NF-e 4.00 exige RSA-SHA1; o JDK recusa SHA-1 com secureValidation ligada (só afeta a validação do teste)
        contexto.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);
        assertTrue(XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(contexto).validate(contexto),
                "a serialização não pode alterar o texto assinado");
    }

    @Test
    void naoFazNadaQuandoODocumentoJaNaoEstaEmRascunhoOuEstaTravadoPorOutraInstancia() {
        when(documentoRepository.buscarPorIdEStatusComLock(any(), eq(StatusDocumentoFiscal.RASCUNHO)))
                .thenReturn(Optional.empty());

        assertFalse(service.assinarSeRascunho(UUID.randomUUID()));

        verifyNoInteractions(documentoService, certificadoRepository);
    }

    @Test
    void semCertificadoAtivoNaoAssinaENaoTransiciona() {
        DocumentoFiscal documento = rascunho();
        when(documentoRepository.buscarPorIdEStatusComLock(any(), eq(StatusDocumentoFiscal.RASCUNHO)))
                .thenReturn(Optional.of(documento));
        when(certificadoRepository.findByTenantIdAndEmitenteId(TENANT_ID, EMITENTE_ID)).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class, () -> service.assinarSeRascunho(UUID.randomUUID()));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
        assertNull(documento.getChaveAcesso());
        verifyNoInteractions(documentoService);
    }

    @Test
    void certificadoVencidoDepoisDoPostNaoAssina() {
        DocumentoFiscal documento = rascunho();
        when(documentoRepository.buscarPorIdEStatusComLock(any(), eq(StatusDocumentoFiscal.RASCUNHO)))
                .thenReturn(Optional.of(documento));
        when(certificadoRepository.findByTenantIdAndEmitenteId(TENANT_ID, EMITENTE_ID))
                .thenReturn(Optional.of(certificado(OffsetDateTime.now().minusDays(1), true)));

        assertThrows(BusinessException.class, () -> service.assinarSeRascunho(UUID.randomUUID()));

        assertNull(documento.getXmlAssinado());
        verifyNoInteractions(documentoService);
    }

    // ---- helpers ----

    private DocumentoFiscal rascunho() {
        DocumentoFiscal d = new DocumentoFiscal();
        d.setTenantId(TENANT_ID);
        d.setEmitenteId(EMITENTE_ID);
        d.setDocumento("NFE");
        d.setModelo("55");
        d.setSerie("1");
        d.setNumero(123L);
        d.setStatus(StatusDocumentoFiscal.RASCUNHO);
        d.setAmbiente("HOMOLOGACAO");
        d.setPayloadRecebido(json.writeValueAsString(request()));
        d.setIdempotencyKey("k-1");
        return d;
    }

    private static CertificadoDigital certificado(OffsetDateTime validoAte, boolean ativo) {
        CertificadoDigital c = new CertificadoDigital();
        c.setTenantId(TENANT_ID);
        c.setEmitenteId(EMITENTE_ID);
        c.setCnpjSubject(CNPJ);
        c.setSenhaCifrada(new byte[]{1});
        c.setCertificadoValidoAte(validoAte);
        c.setAtivo(ativo);
        return c;
    }

    private static DocumentoFiscalRequestDTO request() {
        EnderecoDTO rj = new EnderecoDTO("Rua A", "10", null, "Centro", "3304557", "Rio de Janeiro", "RJ", "20040020", null);
        SnapshotFiscalItemDTO fiscal = new SnapshotFiscalItemDTO("5102", "00", "000", "000001",
                CEM, new BigDecimal("18.00"), new BigDecimal("18.00"), null, null,
                CEM, new BigDecimal("0.10"), new BigDecimal("0.05"), new BigDecimal("0.90"), null,
                new BigDecimal("0.10"), new BigDecimal("0.05"), new BigDecimal("0.90"),
                "01", CEM, new BigDecimal("1.65"), new BigDecimal("1.65"),
                "01", CEM, new BigDecimal("7.60"), new BigDecimal("7.60"),
                null, null, null, null, null, null);
        ItemDocumentoDTO item = new ItemDocumentoDTO("P1", "Produto teste", "12345678", "0", "UN",
                new BigDecimal("1.0000"), CEM, CEM, fiscal);
        return new DocumentoFiscalRequestDTO(EMITENTE_ID, "NFE", "55", "1", "HOMOLOGACAO", "Venda de mercadoria",
                1, 0, 1,
                new EmitenteDTO(CNPJ, "Empresa Teste Ltda", null, "123456789", "3", rj),
                new DestinatarioDTO("12345678909", "Cliente Teste", "1", "123456789", null, rj),
                CEM, List.of(item));
    }
}
