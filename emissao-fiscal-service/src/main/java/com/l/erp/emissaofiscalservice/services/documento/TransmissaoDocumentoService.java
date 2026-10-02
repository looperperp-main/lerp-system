package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.api.dto.DocumentoFiscalRequestDTO;
import com.l.erp.emissaofiscalservice.domain.AmbienteEmissao;
import com.l.erp.emissaofiscalservice.domain.CertificadoDigital;
import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.ServicoWebservice;
import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.TipoDocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.CertificadoDigitalRepository;
import com.l.erp.emissaofiscalservice.repository.DocumentoFiscalRepository;
import com.l.erp.emissaofiscalservice.services.certificado.CertificadoDigitalService;
import com.l.erp.emissaofiscalservice.services.crypto.EnvelopeEncryptionService;
import com.l.erp.emissaofiscalservice.services.endpoint.EndpointResolverService;
import com.l.erp.emissaofiscalservice.services.nfe.NfeMensagens;
import com.l.erp.emissaofiscalservice.services.nfe.NfeMensagens.Mensagem;
import com.l.erp.emissaofiscalservice.services.nfe.NfeRetorno.ProtNFe;
import com.l.erp.emissaofiscalservice.services.nfe.NfeRetornoParser;
import com.l.erp.emissaofiscalservice.services.soap.WebServiceClient;
import com.l.erp.emissaofiscalservice.services.soap.WebServiceComunicacaoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;

/**
 * Transmissão da NF-e à SEFAZ (spec §3 item 10 e §5 Etapa 2). Cada passo roda na própria transação:
 * {@link #iniciar} grava {@code TRANSMITIDO} <b>antes</b> de qualquer envio; {@link #processar} decide, com
 * o documento travado ({@code SKIP LOCKED}), o que perguntar/enviar à SEFAZ.
 *
 * <p>Regra de ouro: nunca reenviar às cegas. Sem recibo, consulta-se a situação pela chave; só
 * {@code cStat 217} (SEFAZ não recebeu) libera o envio. Timeout, HTTP != 200 ou retorno ilegível não mudam o
 * estado: contam uma tentativa, agendam backoff e, ao esgotar, vão para {@code ERRO} avisando o responsável.</p>
 */
@Service
public class TransmissaoDocumentoService {

    private static final Logger log = LoggerFactory.getLogger(TransmissaoDocumentoService.class);

    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final CertificadoDigitalRepository certificadoDigitalRepository;
    private final CertificadoDigitalService certificadoDigitalService;
    private final EnvelopeEncryptionService envelopeEncryptionService;
    private final DocumentoFiscalService documentoFiscalService;
    private final AlertaOperacionalService alertaOperacionalService;
    private final EndpointResolverService endpointResolverService;
    private final WebServiceClient webServiceClient;
    private final NfeRetornoParser parser;
    private final ObjectMapper objectMapper;

    public TransmissaoDocumentoService(DocumentoFiscalRepository documentoFiscalRepository,
                                       CertificadoDigitalRepository certificadoDigitalRepository,
                                       CertificadoDigitalService certificadoDigitalService,
                                       EnvelopeEncryptionService envelopeEncryptionService,
                                       DocumentoFiscalService documentoFiscalService,
                                       AlertaOperacionalService alertaOperacionalService,
                                       EndpointResolverService endpointResolverService,
                                       WebServiceClient webServiceClient, NfeRetornoParser parser,
                                       ObjectMapper objectMapper) {
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.certificadoDigitalRepository = certificadoDigitalRepository;
        this.certificadoDigitalService = certificadoDigitalService;
        this.envelopeEncryptionService = envelopeEncryptionService;
        this.documentoFiscalService = documentoFiscalService;
        this.alertaOperacionalService = alertaOperacionalService;
        this.endpointResolverService = endpointResolverService;
        this.webServiceClient = webServiceClient;
        this.parser = parser;
        this.objectMapper = objectMapper;
    }

    /** ASSINADO → TRANSMITIDO, comitado antes do envio. {@code false} se o documento já não está ASSINADO. */
    @Transactional
    public boolean iniciar(UUID documentoId) {
        var travado = documentoFiscalRepository.buscarPorIdEStatusComLock(documentoId, StatusDocumentoFiscal.ASSINADO);
        if (travado.isEmpty()) {
            return false;
        }
        DocumentoFiscal documento = travado.get();
        documento.setProximaTentativaEm(null);
        documentoFiscalService.aplicarTransicao(documento, StatusDocumentoFiscal.TRANSMITIDO);
        return true;
    }

    /**
     * Um passo de conversa com a SEFAZ para um documento {@code TRANSMITIDO}. Não propaga falha de
     * comunicação: ela vira tentativa contada + backoff (ou {@code ERRO}), dentro da mesma transação.
     */
    @Transactional
    public void processar(UUID documentoId) {
        var travado = documentoFiscalRepository.buscarPorIdEStatusComLock(documentoId, StatusDocumentoFiscal.TRANSMITIDO);
        if (travado.isEmpty()) {
            return;
        }
        DocumentoFiscal documento = travado.get();
        try {
            DocumentoFiscalRequestDTO request = objectMapper.readValue(documento.getPayloadRecebido(), DocumentoFiscalRequestDTO.class);
            Sessao sessao = abrirSessao(documento, request);
            try {
                if (documento.getRecibo() != null) {
                    consultarRecibo(documento, sessao);
                } else {
                    consultarSituacaoOuEnviar(documento, sessao);
                }
            } finally {
                Arrays.fill(sessao.senha(), '\0');
            }
        } catch (BusinessException falha) {
            // configuração/certificado/endpoint: repetir não resolve
            log.warn("Documento {} não pôde ser transmitido: {}", documento.getId(), falha.getMessage());
            documento.setUltimoErro(falha.getMessage());
            encerrarEmErro(documento);
        } catch (WebServiceComunicacaoException | IllegalStateException falha) {
            registrarFalhaRetentavel(documento, falha);
        }
    }

    // ---- passos ----

    private void consultarSituacaoOuEnviar(DocumentoFiscal documento, Sessao sessao) {
        Mensagem consulta = NfeMensagens.consSitNFe(NfeMensagens.tpAmb(documento.getAmbiente()), documento.getChaveAcesso());
        var situacao = parser.lerConsultaSituacao(chamar(documento, sessao, ServicoWebservice.NFE_CONSULTA_PROTOCOLO, consulta));
        if (situacao.protNFe() != null) {
            interpretarProtocolo(documento, situacao.protNFe());
        } else if (Constants.NFE_CSTAT_NAO_CONSTA_NA_BASE.equals(situacao.cStat())) {
            enviarLote(documento, sessao); // a SEFAZ confirmou que nunca recebeu: enviar é seguro
        } else {
            throw new IllegalStateException("Consulta de situação com cStat " + situacao.cStat() + " " + situacao.xMotivo());
        }
    }

    private void enviarLote(DocumentoFiscal documento, Sessao sessao) {
        Mensagem envio = NfeMensagens.enviNFe(documento.getNumero(), documento.getXmlAssinado());
        var retorno = parser.lerEnvio(chamar(documento, sessao, ServicoWebservice.NFE_AUTORIZACAO, envio));
        documento.setUltimaMensagemSefaz(retorno.cStat() + " - " + retorno.xMotivo());
        if (Constants.NFE_CSTAT_LOTE_RECEBIDO.equals(retorno.cStat()) && retorno.infRec() != null) {
            documento.setRecibo(retorno.infRec().nRec());
            agendar(documento, Constants.EMISSAO_TRANSMISSAO_ESPERA_LOTE_SEGUNDOS);
        } else if (Constants.NFE_CSTAT_LOTE_PROCESSADO.equals(retorno.cStat()) && retorno.protNFe() != null) {
            interpretarProtocolo(documento, retorno.protNFe()); // SEFAZ processou na hora: o desfecho está no protNFe, não no lote
        } else if (Constants.NFE_CSTAT_DUPLICIDADE.equals(retorno.cStat())) {
            agendar(documento, Constants.EMISSAO_TRANSMISSAO_ESPERA_LOTE_SEGUNDOS); // próxima rodada consulta pela chave
        } else {
            rejeitar(documento, retorno.cStat(), retorno.xMotivo()); // lote rejeitado: nenhuma NF-e foi processada
        }
    }

    private void consultarRecibo(DocumentoFiscal documento, Sessao sessao) {
        Mensagem consulta = NfeMensagens.consReciNFe(NfeMensagens.tpAmb(documento.getAmbiente()), documento.getRecibo());
        var retorno = parser.lerConsultaRecibo(chamar(documento, sessao, ServicoWebservice.NFE_RET_AUTORIZACAO, consulta));
        if (Constants.NFE_CSTAT_LOTE_EM_PROCESSAMENTO.equals(retorno.cStat())) {
            agendar(documento, Constants.EMISSAO_TRANSMISSAO_ESPERA_LOTE_SEGUNDOS);
        } else if (Constants.NFE_CSTAT_LOTE_PROCESSADO.equals(retorno.cStat()) && retorno.protNFe() != null) {
            interpretarProtocolo(documento, retorno.protNFe());
        } else {
            throw new IllegalStateException("Consulta de recibo com cStat " + retorno.cStat() + " " + retorno.xMotivo());
        }
    }

    private void interpretarProtocolo(DocumentoFiscal documento, ProtNFe protocolo) {
        var inf = protocolo.infProt();
        documento.setUltimaMensagemSefaz(inf.cStat() + " - " + inf.xMotivo());
        documento.setProximaTentativaEm(null);
        if (Constants.NFE_CSTAT_AUTORIZADA.contains(inf.cStat())) {
            documento.setProtocolo(inf.nProt());
            documentoFiscalService.aplicarTransicao(documento, StatusDocumentoFiscal.AUTORIZADO);
        } else if (Constants.NFE_CSTAT_DENEGADA.contains(inf.cStat())) {
            documento.setProtocolo(inf.nProt());
            documentoFiscalService.aplicarTransicao(documento, StatusDocumentoFiscal.DENEGADO);
        } else {
            rejeitar(documento, inf.cStat(), inf.xMotivo());
        }
    }

    private void rejeitar(DocumentoFiscal documento, String cStat, String xMotivo) {
        documento.setUltimaMensagemSefaz(cStat + " - " + xMotivo);
        documento.setProximaTentativaEm(null);
        documentoFiscalService.aplicarTransicao(documento, StatusDocumentoFiscal.REJEITADO);
    }

    // ---- infra ----

    private String chamar(DocumentoFiscal documento, Sessao sessao, ServicoWebservice servico, Mensagem mensagem) {
        String url = endpointResolverService.resolverUrl(sessao.uf(), TipoDocumentoFiscal.valueOf(documento.getDocumento()),
                servico, AmbienteEmissao.valueOf(documento.getAmbiente().toUpperCase()), false);
        return webServiceClient.enviar(URI.create(url), mensagem.soapAction(), mensagem.corpo(), sessao.keyStore(), sessao.senha());
    }

    private Sessao abrirSessao(DocumentoFiscal documento, DocumentoFiscalRequestDTO request) {
        CertificadoDigital certificado = certificadoDigitalRepository
                .findByTenantIdAndEmitenteId(documento.getTenantId(), documento.getEmitenteId())
                .filter(CertificadoDigital::isAtivo)
                .orElseThrow(() -> new BusinessException(Constants.EMISSAO_ERRO_CERTIFICADO_AUSENTE, HttpStatus.UNPROCESSABLE_ENTITY));
        if (certificado.getCertificadoValidoAte().isBefore(OffsetDateTime.now())) {
            throw new BusinessException(Constants.EMISSAO_ERRO_CERTIFICADO_VENCIDO, HttpStatus.UNPROCESSABLE_ENTITY);
        }
        certificadoDigitalService.exigirPosse(certificado, documento.getTenantId(), documento.getEmitenteId());
        char[] senha = new String(certificadoDigitalService.decifrar(certificado, certificado.getSenhaCifrada()), StandardCharsets.UTF_8).toCharArray();
        try {
            return new Sessao(request.emitente().endereco().uf(), certificadoDigitalService.abrirKeyStore(certificado, senha), senha);
        } catch (RuntimeException falha) {
            Arrays.fill(senha, '\0');
            throw falha;
        }
    }

    private void registrarFalhaRetentavel(DocumentoFiscal documento, Exception falha) {
        short tentativas = (short) (documento.getTentativasTransmissao() + 1);
        documento.setTentativasTransmissao(tentativas);
        log.error("Falha ao transmitir o documento {} (tentativa {}/{})", documento.getId(), tentativas,
                Constants.EMISSAO_TRANSMISSAO_MAX_TENTATIVAS, falha);
        if (tentativas >= Constants.EMISSAO_TRANSMISSAO_MAX_TENTATIVAS) {
            documento.setUltimoErro(falha instanceof WebServiceComunicacaoException
                    ? Constants.EMISSAO_ERRO_TRANSMISSAO_SEM_RESPOSTA : Constants.EMISSAO_ERRO_TRANSMISSAO_INTERNO);
            encerrarEmErro(documento);
            return;
        }
        documento.setUltimoErro(Constants.EMISSAO_ERRO_TRANSMISSAO_INTERNO);
        agendar(documento, atrasoEmSegundos(tentativas));
        documentoFiscalRepository.save(documento);
    }

    private void encerrarEmErro(DocumentoFiscal documento) {
        documento.setProximaTentativaEm(null);
        documentoFiscalService.aplicarTransicao(documento, StatusDocumentoFiscal.ERRO);
        alertaOperacionalService.alertarDocumentoEmErro(documento);
    }

    private static void agendar(DocumentoFiscal documento, long segundos) {
        documento.setProximaTentativaEm(OffsetDateTime.now().plusSeconds(segundos));
    }

    /** 2^(tentativa-1) × base: 10s, 20s, 40s, 80s. */
    static long atrasoEmSegundos(int tentativa) {
        return Constants.EMISSAO_TRANSMISSAO_BACKOFF_BASE_SEGUNDOS << (tentativa - 1);
    }

    private record Sessao(String uf, KeyStore keyStore, char[] senha) {}
}
