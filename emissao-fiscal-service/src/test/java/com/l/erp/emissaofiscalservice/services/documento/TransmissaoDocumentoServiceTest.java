package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.api.dto.DocumentoFiscalRequestDTO;
import com.l.erp.emissaofiscalservice.domain.CertificadoDigital;
import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.CertificadoDigitalRepository;
import com.l.erp.emissaofiscalservice.repository.DocumentoFiscalRepository;
import com.l.erp.emissaofiscalservice.services.certificado.CertificadoDigitalService;
import com.l.erp.emissaofiscalservice.services.crypto.EnvelopeEncryptionService;
import com.l.erp.emissaofiscalservice.services.endpoint.EndpointResolverService;
import com.l.erp.emissaofiscalservice.services.nfe.NfeRetornoParser;
import com.l.erp.emissaofiscalservice.services.soap.WebServiceClient;
import com.l.erp.emissaofiscalservice.services.soap.WebServiceComunicacaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Transmissão à SEFAZ (spec §3 item 10): grava TRANSMITIDO antes de enviar, nunca reenvia às cegas
 * (consulta por chave; só cStat 217 libera o envio) e traduz o retorno em estado. Retornos XML fixos,
 * {@code WebServiceClient} mockado — nada de rede.
 */
@ExtendWith(MockitoExtension.class)
class TransmissaoDocumentoServiceTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String CHAVE = "35260112345678000195550010000000421123456780";

    @Mock
    private DocumentoFiscalRepository repository;
    @Mock
    private CertificadoDigitalRepository certificadoRepository;
    @Mock
    private CertificadoDigitalService certificadoService;
    @Mock
    private EnvelopeEncryptionService criptografia;
    @Mock
    private DocumentoFiscalService documentoService;
    @Mock
    private AlertaOperacionalService alerta;
    @Mock
    private EndpointResolverService endpoints;
    @Mock
    private WebServiceClient webService;
    @Mock
    private ObjectMapper objectMapper;

    private TransmissaoDocumentoService service;

    @BeforeEach
    void setUp() {
        service = new TransmissaoDocumentoService(repository, certificadoRepository, certificadoService, criptografia,
                documentoService, alerta, endpoints, webService, new NfeRetornoParser(), objectMapper);
    }

    @Test
    void iniciarGravaTransmitidoAntesDeQualquerEnvio() {
        DocumentoFiscal documento = documento(StatusDocumentoFiscal.ASSINADO);
        when(repository.buscarPorIdEStatusComLock(ID, StatusDocumentoFiscal.ASSINADO)).thenReturn(Optional.of(documento));

        assertTrue(service.iniciar(ID));

        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.TRANSMITIDO);
        verifyNoInteractions(webService);
    }

    @Test
    void iniciarIgnoraDocumentoQueJaNaoEstaAssinado() {
        when(repository.buscarPorIdEStatusComLock(ID, StatusDocumentoFiscal.ASSINADO)).thenReturn(Optional.empty());

        assertFalse(service.iniciar(ID));
    }

    @Test
    void semReciboConsultaPorChaveEEnviaSoSeASefazNuncaRecebeu() {
        DocumentoFiscal documento = transmitido(null);
        prepararSessao(documento);
        when(webService.enviar(any(), contains("nfeConsultaNF"), any(), any(), any()))
                .thenReturn(soap("<retConsSitNFe><cStat>217</cStat><xMotivo>NF-e nao consta na base</xMotivo></retConsSitNFe>"));
        when(webService.enviar(any(), contains("nfeAutorizacaoLote"), any(), any(), any()))
                .thenReturn(soap("<retEnviNFe><cStat>103</cStat><xMotivo>Lote recebido</xMotivo>"
                        + "<infRec><nRec>351000000012345</nRec><tMed>1</tMed></infRec></retEnviNFe>"));

        service.processar(ID);

        assertEquals("351000000012345", documento.getRecibo());
        assertNotNull(documento.getProximaTentativaEm());
        verify(documentoService, never()).aplicarTransicao(any(), any());
    }

    @Test
    void semReciboEComNfeJaAutorizadaNaSefazNaoReenviaEAutoriza() {
        DocumentoFiscal documento = transmitido(null);
        prepararSessao(documento);
        when(webService.enviar(any(), contains("nfeConsultaNF"), any(), any(), any()))
                .thenReturn(soap("<retConsSitNFe><cStat>100</cStat><xMotivo>Autorizado</xMotivo><chNFe>" + CHAVE + "</chNFe>"
                        + protocolo("100", "Autorizado o uso da NF-e", "135260000000001") + "</retConsSitNFe>"));

        service.processar(ID);

        assertEquals("135260000000001", documento.getProtocolo());
        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.AUTORIZADO);
        verify(webService, times(1)).enviar(any(), any(), any(), any(), any());
        verify(webService, never()).enviar(any(), contains("nfeAutorizacaoLote"), any(), any(), any());
    }

    @Test
    void comReciboELoteProcessadoAutorizaComProtocolo() {
        DocumentoFiscal documento = transmitido("351000000012345");
        prepararSessao(documento);
        when(webService.enviar(any(), contains("nfeRetAutorizacaoLote"), any(), any(), any()))
                .thenReturn(soap("<retConsReciNFe><cStat>104</cStat><xMotivo>Lote processado</xMotivo>"
                        + protocolo("100", "Autorizado o uso da NF-e", "135260000000002") + "</retConsReciNFe>"));

        service.processar(ID);

        assertEquals("135260000000002", documento.getProtocolo());
        assertEquals("100 - Autorizado o uso da NF-e", documento.getUltimaMensagemSefaz());
        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.AUTORIZADO);
    }

    @Test
    void envioJaProcessadoNaHoraLeODesfechoDoProtocoloEmVezDeRejeitarOLote() {
        DocumentoFiscal documento = transmitido(null);
        prepararSessao(documento);
        when(webService.enviar(any(), contains("nfeConsultaNF"), any(), any(), any()))
                .thenReturn(soap("<retConsSitNFe><cStat>217</cStat><xMotivo>NF-e nao consta na base</xMotivo></retConsSitNFe>"));
        when(webService.enviar(any(), contains("nfeAutorizacaoLote"), any(), any(), any()))
                .thenReturn(soap("<retEnviNFe><cStat>104</cStat><xMotivo>Lote processado</xMotivo>"
                        + protocolo("209", "Rejeicao: IE do emitente invalida", null) + "</retEnviNFe>"));

        service.processar(ID);

        assertEquals("209 - Rejeicao: IE do emitente invalida", documento.getUltimaMensagemSefaz());
        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.REJEITADO);
    }

    @Test
    void notaDenegadaVaiParaDenegado() {
        DocumentoFiscal documento = transmitido("351000000012345");
        prepararSessao(documento);
        when(webService.enviar(any(), any(), any(), any(), any()))
                .thenReturn(soap("<retConsReciNFe><cStat>104</cStat><xMotivo>Lote processado</xMotivo>"
                        + protocolo("302", "Uso Denegado: irregularidade fiscal do destinatario", "135260000000003") + "</retConsReciNFe>"));

        service.processar(ID);

        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.DENEGADO);
    }

    @Test
    void rejeicaoDaNotaVaiParaRejeitadoComOMotivoDaSefaz() {
        DocumentoFiscal documento = transmitido("351000000012345");
        prepararSessao(documento);
        when(webService.enviar(any(), any(), any(), any(), any()))
                .thenReturn(soap("<retConsReciNFe><cStat>104</cStat><xMotivo>Lote processado</xMotivo>"
                        + protocolo("539", "Rejeicao: Duplicidade de NF-e com diferenca na Chave de Acesso", null) + "</retConsReciNFe>"));

        service.processar(ID);

        assertEquals("539 - Rejeicao: Duplicidade de NF-e com diferenca na Chave de Acesso", documento.getUltimaMensagemSefaz());
        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.REJEITADO);
    }

    @Test
    void loteAindaEmProcessamentoSoAgendaNovaConsulta() {
        DocumentoFiscal documento = transmitido("351000000012345");
        prepararSessao(documento);
        when(webService.enviar(any(), any(), any(), any(), any()))
                .thenReturn(soap("<retConsReciNFe><cStat>105</cStat><xMotivo>Lote em processamento</xMotivo></retConsReciNFe>"));

        service.processar(ID);

        assertNotNull(documento.getProximaTentativaEm());
        assertEquals(0, documento.getTentativasTransmissao(), "esperar o lote não é falha");
        verify(documentoService, never()).aplicarTransicao(any(), any());
    }

    @Test
    void timeoutNaoReenviaContaTentativaEAgendaBackoff() {
        DocumentoFiscal documento = transmitido(null);
        prepararSessao(documento);
        when(webService.enviar(any(), any(), any(), any(), any())).thenThrow(new WebServiceComunicacaoException("timeout"));
        OffsetDateTime antes = OffsetDateTime.now();

        service.processar(ID);

        assertEquals(1, documento.getTentativasTransmissao());
        assertTrue(documento.getProximaTentativaEm().isAfter(antes.plusSeconds(9)), "1ª espera é de 10s");
        assertEquals(Constants.EMISSAO_ERRO_TRANSMISSAO_INTERNO, documento.getUltimoErro());
        verify(repository).save(documento);
        verify(webService, times(1)).enviar(any(), any(), any(), any(), any()); // só a consulta, nunca o envio
        verifyNoInteractions(alerta);
    }

    @Test
    void retornoIlegivelContaComoFalhaRetentavel() {
        DocumentoFiscal documento = transmitido("351000000012345");
        prepararSessao(documento);
        when(webService.enviar(any(), any(), any(), any(), any())).thenReturn("nao e xml");

        service.processar(ID);

        assertEquals(1, documento.getTentativasTransmissao());
        verify(documentoService, never()).aplicarTransicao(any(), any());
    }

    @Test
    void quintaFalhaVaiParaErroEAvisa() {
        DocumentoFiscal documento = transmitido(null);
        documento.setTentativasTransmissao((short) (Constants.EMISSAO_TRANSMISSAO_MAX_TENTATIVAS - 1));
        prepararSessao(documento);
        when(webService.enviar(any(), any(), any(), any(), any())).thenThrow(new WebServiceComunicacaoException("timeout"));

        service.processar(ID);

        assertEquals(Constants.EMISSAO_ERRO_TRANSMISSAO_SEM_RESPOSTA, documento.getUltimoErro());
        assertNull(documento.getProximaTentativaEm());
        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.ERRO);
        verify(alerta).alertarDocumentoEmErro(documento);
    }

    @Test
    void certificadoAusenteVaiParaErroNaPrimeiraEAvisa() {
        DocumentoFiscal documento = transmitido(null);
        when(repository.buscarPorIdEStatusComLock(ID, StatusDocumentoFiscal.TRANSMITIDO)).thenReturn(Optional.of(documento));
        when(objectMapper.readValue(any(String.class), eq(DocumentoFiscalRequestDTO.class))).thenReturn(mock(DocumentoFiscalRequestDTO.class));
        when(certificadoRepository.findByTenantIdAndEmitenteId(any(), any())).thenReturn(Optional.empty());

        service.processar(ID);

        assertEquals(Constants.EMISSAO_ERRO_CERTIFICADO_AUSENTE, documento.getUltimoErro());
        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.ERRO);
        verify(alerta).alertarDocumentoEmErro(documento);
        verifyNoInteractions(webService);
    }

    @Test
    void documentoQueJaNaoEstaTransmitidoNaoEhTocado() {
        when(repository.buscarPorIdEStatusComLock(ID, StatusDocumentoFiscal.TRANSMITIDO)).thenReturn(Optional.empty());

        service.processar(ID);

        verifyNoInteractions(webService, documentoService, alerta);
    }

    @Test
    void backoffDobraACadaTentativa() {
        assertEquals(10, TransmissaoDocumentoService.atrasoEmSegundos(1));
        assertEquals(20, TransmissaoDocumentoService.atrasoEmSegundos(2));
        assertEquals(80, TransmissaoDocumentoService.atrasoEmSegundos(4));
    }

    // ---- helpers ----

    /** Deixa o caminho até a chamada ao {@code WebServiceClient} pronto (certificado válido, endpoint resolvido). */
    private void prepararSessao(DocumentoFiscal documento) {
        when(repository.buscarPorIdEStatusComLock(ID, StatusDocumentoFiscal.TRANSMITIDO)).thenReturn(Optional.of(documento));
        DocumentoFiscalRequestDTO request = mock(DocumentoFiscalRequestDTO.class, RETURNS_DEEP_STUBS);
        when(request.emitente().endereco().uf()).thenReturn("SP");
        when(objectMapper.readValue(any(String.class), eq(DocumentoFiscalRequestDTO.class))).thenReturn(request);
        CertificadoDigital certificado = mock(CertificadoDigital.class);
        when(certificado.isAtivo()).thenReturn(true);
        when(certificado.getCertificadoValidoAte()).thenReturn(OffsetDateTime.now().plusDays(90));
        when(certificadoRepository.findByTenantIdAndEmitenteId(any(), any())).thenReturn(Optional.of(certificado));
        when(criptografia.decifrar(any())).thenReturn("senha".getBytes());
        when(endpoints.resolverUrl(eq("SP"), any(), any(), any(), anyBoolean())).thenReturn("https://svrs.exemplo/ws");
    }

    private static DocumentoFiscal transmitido(String recibo) {
        DocumentoFiscal d = documento(StatusDocumentoFiscal.TRANSMITIDO);
        d.setRecibo(recibo);
        return d;
    }

    private static DocumentoFiscal documento(StatusDocumentoFiscal status) {
        DocumentoFiscal d = new DocumentoFiscal();
        d.setTenantId(9L);
        d.setEmitenteId(UUID.randomUUID());
        d.setDocumento("NFE");
        d.setAmbiente("HOMOLOGACAO");
        d.setSerie("1");
        d.setNumero(42L);
        d.setStatus(status);
        d.setChaveAcesso(CHAVE);
        d.setXmlAssinado("<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"/>");
        d.setPayloadRecebido("{}");
        return d;
    }

    private static String protocolo(String cStat, String xMotivo, String nProt) {
        return "<protNFe versao=\"4.00\"><infProt><chNFe>" + CHAVE + "</chNFe><dhRecbto>2026-09-29T10:00:00-03:00</dhRecbto>"
                + (nProt == null ? "" : "<nProt>" + nProt + "</nProt>")
                + "<cStat>" + cStat + "</cStat><xMotivo>" + xMotivo + "</xMotivo></infProt></protNFe>";
    }

    private static String soap(String retorno) {
        return "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\"><soap:Body>"
                + "<nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/X\">"
                + retorno.replaceFirst("^<(\\w+)", "<$1 xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\"")
                + "</nfeResultMsg></soap:Body></soap:Envelope>";
    }
}
