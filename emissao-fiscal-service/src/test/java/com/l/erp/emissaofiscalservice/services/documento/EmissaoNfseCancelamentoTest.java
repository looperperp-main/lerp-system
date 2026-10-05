package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.domain.CertificadoDigital;
import com.l.erp.emissaofiscalservice.domain.CodigoProvedorNfse;
import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.CertificadoDigitalRepository;
import com.l.erp.emissaofiscalservice.repository.DocumentoFiscalRepository;
import com.l.erp.emissaofiscalservice.services.certificado.CertificadoDigitalService;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfse;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfse.ResultadoNfse;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfse.ResultadoNfse.Situacao;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfseResolver;
import com.l.erp.emissaofiscalservice.services.soap.WebServiceComunicacaoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cancelamento assíncrono da NFS-e (pedido registrado na API, efetivado pelo job) e DANFS-e. */
@ExtendWith(MockitoExtension.class)
class EmissaoNfseCancelamentoTest {

    private static final Long TENANT = 9L;
    private static final UUID ID = UUID.randomUUID();
    private static final String MOTIVO = "Erro na emissao: valor do servico digitado errado";

    @Mock
    private DocumentoFiscalRepository repository;
    @Mock
    private CertificadoDigitalRepository certificadoRepository;
    @Mock
    private CertificadoDigitalService certificadoService;
    @Mock
    private DocumentoFiscalService documentoService;
    @Mock
    private AlertaOperacionalService alerta;
    @Mock
    private ProvedorNfseResolver resolver;
    @Mock
    private ProvedorNfse provedor;

    private EmissaoNfseService service;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.addHeader(Constants.HEADER_TENANT_ID, String.valueOf(TENANT));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requisicao));
        service = new EmissaoNfseService(repository, certificadoRepository, certificadoService, documentoService,
                alerta, resolver);
    }

    @AfterEach
    void limpar() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ---- solicitação (API) ----

    @Test
    void solicitarGravaOMotivoSemTocarNoProvedor() {
        DocumentoFiscal documento = autorizada(null);
        when(repository.findByIdAndTenantId(ID, TENANT)).thenReturn(Optional.of(documento));

        service.solicitarCancelamento(ID, "  " + MOTIVO + "  ");

        assertEquals(MOTIVO, documento.getMotivoCancelamento());
        verify(repository).save(documento);
        verify(provedor, never()).cancelar(any(), any(), any());
    }

    @Test
    void solicitarDeNovoComPedidoPendenteNaoReescreve() {
        DocumentoFiscal documento = autorizada("motivo anterior com mais de quinze");
        when(repository.findByIdAndTenantId(ID, TENANT)).thenReturn(Optional.of(documento));

        service.solicitarCancelamento(ID, MOTIVO);

        assertEquals("motivo anterior com mais de quinze", documento.getMotivoCancelamento());
        verify(repository, never()).save(any());
    }

    @Test
    void motivoForaDe15a255Eh400() {
        when(repository.findByIdAndTenantId(ID, TENANT)).thenReturn(Optional.of(autorizada(null)));

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(BusinessException.class, () -> service.solicitarCancelamento(ID, "curto")).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(BusinessException.class, () -> service.solicitarCancelamento(ID, "x".repeat(256))).getStatus());
    }

    @Test
    void soNfseAutorizadaPodeSerCancelada() {
        DocumentoFiscal rascunho = autorizada(null);
        rascunho.setStatus(StatusDocumentoFiscal.RASCUNHO);
        when(repository.findByIdAndTenantId(ID, TENANT)).thenReturn(Optional.of(rascunho));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,
                assertThrows(BusinessException.class, () -> service.solicitarCancelamento(ID, MOTIVO)).getStatus());
    }

    @Test
    void documentoDeOutroTenantOuInexistenteEh404() {
        when(repository.findByIdAndTenantId(ID, TENANT)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.solicitarCancelamento(ID, MOTIVO)).getStatus());
    }

    // ---- passo do job ----

    @Test
    void canceladaNoProvedorVaiParaCanceladoELimpaOPedido() {
        DocumentoFiscal documento = prepararJob(MOTIVO);
        when(provedor.cancelar(any(), any(), eq(MOTIVO))).thenReturn(new ResultadoNfse(Situacao.CANCELADA, "chave", null, null));

        service.cancelar(ID);

        assertNull(documento.getMotivoCancelamento());
        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.CANCELADO);
    }

    @Test
    void recusadaPeloProvedorMantemAutorizadaELimpaOPedidoComOMotivoDaRecusa() {
        DocumentoFiscal documento = prepararJob(MOTIVO);
        when(provedor.cancelar(any(), any(), any())).thenReturn(new ResultadoNfse(Situacao.REJEITADA, "chave", null, "prazo expirado"));

        service.cancelar(ID);

        assertNull(documento.getMotivoCancelamento());
        assertEquals(Constants.EMISSAO_ERRO_NFSE_CANCELAMENTO_RECUSADO.formatted("prazo expirado"), documento.getUltimoErro());
        verify(documentoService, never()).aplicarTransicao(any(), any());
    }

    @Test
    void timeoutAgendaNovaTentativaMantendoOPedido() {
        DocumentoFiscal documento = prepararJob(MOTIVO);
        when(provedor.cancelar(any(), any(), any())).thenThrow(new WebServiceComunicacaoException("timeout"));

        service.cancelar(ID);

        assertEquals(MOTIVO, documento.getMotivoCancelamento());
        assertEquals(1, documento.getTentativasTransmissao());
        assertNotNull(documento.getProximaTentativaEm());
        verify(repository).save(documento);
    }

    @Test
    void tentativasEsgotadasDesistemSemMudarOEstadoDaNota() {
        DocumentoFiscal documento = prepararJob(MOTIVO);
        documento.setTentativasTransmissao((short) (Constants.EMISSAO_TRANSMISSAO_MAX_TENTATIVAS - 1));
        when(provedor.cancelar(any(), any(), any())).thenThrow(new WebServiceComunicacaoException("timeout"));

        service.cancelar(ID);

        assertNull(documento.getMotivoCancelamento());
        assertEquals(Constants.EMISSAO_ERRO_TRANSMISSAO_SEM_RESPOSTA, documento.getUltimoErro());
        verify(documentoService, never()).aplicarTransicao(any(), any());
    }

    @Test
    void semPedidoPendenteOJobNaoFazNada() {
        DocumentoFiscal documento = autorizada(null);
        when(repository.buscarPorIdEStatusComLock(ID, StatusDocumentoFiscal.AUTORIZADO)).thenReturn(Optional.of(documento));

        service.cancelar(ID);

        verify(provedor, never()).cancelar(any(), any(), any());
    }

    // ---- DANFS-e ----

    @Test
    void danfseVemDoProvedorQueEmitiu() {
        DocumentoFiscal documento = autorizada(null);
        when(repository.findByIdAndTenantId(ID, TENANT)).thenReturn(Optional.of(documento));
        prepararSessao();
        byte[] pdf = {1, 2, 3};
        when(provedor.representacaoGrafica(eq(documento), any())).thenReturn(pdf);

        assertArrayEquals(pdf, service.danfse(ID));
    }

    @Test
    void danfseIndisponivelNoProvedorPropaga503() {
        when(repository.findByIdAndTenantId(ID, TENANT)).thenReturn(Optional.of(autorizada(null)));
        prepararSessao();
        when(provedor.representacaoGrafica(any(), any()))
                .thenThrow(new BusinessException(Constants.EMISSAO_ERRO_NFSE_DANFSE_INDISPONIVEL, HttpStatus.SERVICE_UNAVAILABLE));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(BusinessException.class, () -> service.danfse(ID)).getStatus());
    }

    // ---- apoio ----

    private DocumentoFiscal prepararJob(String motivo) {
        DocumentoFiscal documento = autorizada(motivo);
        when(repository.buscarPorIdEStatusComLock(ID, StatusDocumentoFiscal.AUTORIZADO)).thenReturn(Optional.of(documento));
        prepararSessao();
        return documento;
    }

    private void prepararSessao() {
        when(resolver.porCodigo(CodigoProvedorNfse.ADN)).thenReturn(provedor);
        CertificadoDigital certificado = mock(CertificadoDigital.class);
        when(certificado.isAtivo()).thenReturn(true);
        when(certificado.getCertificadoValidoAte()).thenReturn(OffsetDateTime.now().plusDays(90));
        when(certificadoRepository.findByTenantIdAndEmitenteId(any(), any())).thenReturn(Optional.of(certificado));
        when(certificadoService.decifrar(any(), any())).thenReturn("senha".getBytes());
    }

    private static DocumentoFiscal autorizada(String motivoCancelamento) {
        DocumentoFiscal d = new DocumentoFiscal();
        d.setTenantId(TENANT);
        d.setEmitenteId(UUID.randomUUID());
        d.setDocumento("NFSE");
        d.setProvedor(CodigoProvedorNfse.ADN);
        d.setAmbiente("HOMOLOGACAO");
        d.setSerie("1");
        d.setNumero(7L);
        d.setStatus(StatusDocumentoFiscal.AUTORIZADO);
        d.setChaveAcesso("31040072240152424000196000000000000126100123456789");
        d.setPayloadRecebido("{}");
        d.setMotivoCancelamento(motivoCancelamento);
        return d;
    }
}
