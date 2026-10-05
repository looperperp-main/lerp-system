package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.api.dto.EnderecoDTO;
import com.l.erp.emissaofiscalservice.api.dto.NfseRequestDTO;
import com.l.erp.emissaofiscalservice.domain.CodigoProvedorNfse;
import com.l.erp.emissaofiscalservice.domain.ConfiguracaoNfse;
import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.IdempotencyKey;
import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.DocumentoFiscalRepository;
import com.l.erp.emissaofiscalservice.repository.IdempotencyKeyRepository;
import com.l.erp.emissaofiscalservice.repository.OutboxEventoRepository;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfseResolver;
import com.l.erp.emissaofiscalservice.services.numeracao.NumeracaoDocumentoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Criação da NFS-e: provedor gravado no documento, idempotência e guarda antes de gastar número. */
@ExtendWith(MockitoExtension.class)
class DocumentoFiscalServiceNfseTest {

    private static final Long TENANT = 9L;
    private static final UUID EMITENTE = UUID.randomUUID();
    private static final String CHAVE_IDEM = "chave-1";

    @Mock
    private DocumentoFiscalRepository documentos;
    @Mock
    private IdempotencyKeyRepository idempotencias;
    @Mock
    private OutboxEventoRepository outbox;
    @Mock
    private NumeracaoDocumentoService numeracao;
    @Mock
    private DocumentoFiscalGuardaService guardaNfe;
    @Mock
    private GuardaNfseService guardaNfse;
    @Mock
    private ProvedorNfseResolver resolver;

    private DocumentoFiscalService service;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.addHeader(Constants.HEADER_TENANT_ID, String.valueOf(TENANT));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requisicao));
        service = new DocumentoFiscalService(documentos, idempotencias, outbox, numeracao, guardaNfe, guardaNfse,
                resolver, JsonMapper.builder().build());
    }

    @AfterEach
    void limpar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void criaRascunhoNfseComProvedorDaConfiguracaoNumeroEEventoDeOutbox() {
        prepararCriacao();

        DocumentoFiscal documento = service.criarNfse(pedido("100.00"), CHAVE_IDEM);

        assertEquals("NFSE", documento.getDocumento());
        assertNull(documento.getModelo());
        assertEquals(StatusDocumentoFiscal.RASCUNHO, documento.getStatus());
        assertEquals(CodigoProvedorNfse.ADN, documento.getProvedor());
        assertEquals(7L, documento.getNumero());
        assertEquals("1", documento.getSerie());
        verify(guardaNfse).validar(eq(TENANT), any(NfseRequestDTO.class));
        verify(numeracao).proximoNumero(TENANT, EMITENTE, "NFSE", "1");
        verify(outbox).save(any());
        verifyNoInteractions(guardaNfe);
    }

    @Test
    void mesmaChaveEMesmoPedidoDevolveOMesmoDocumentoSemNumerarDeNovo() {
        prepararCriacao();
        DocumentoFiscal criado = service.criarNfse(pedido("100.00"), CHAVE_IDEM);
        ArgumentCaptor<IdempotencyKey> gravada = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(idempotencias).save(gravada.capture());
        when(idempotencias.findByTenantIdAndIdempotencyKey(TENANT, CHAVE_IDEM)).thenReturn(Optional.of(gravada.getValue()));
        when(documentos.findByIdAndTenantId(any(), eq(TENANT))).thenReturn(Optional.of(criado));

        DocumentoFiscal replay = service.criarNfse(pedido("100.00"), CHAVE_IDEM);

        assertSame(criado, replay);
        verify(numeracao).proximoNumero(any(), any(), anyString(), anyString()); // só a 1ª vez
    }

    @Test
    void mesmaChaveComPedidoDiferenteEh409() {
        prepararCriacao();
        service.criarNfse(pedido("100.00"), CHAVE_IDEM);
        ArgumentCaptor<IdempotencyKey> gravada = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(idempotencias).save(gravada.capture());
        when(idempotencias.findByTenantIdAndIdempotencyKey(TENANT, CHAVE_IDEM)).thenReturn(Optional.of(gravada.getValue()));

        BusinessException e = assertThrows(BusinessException.class, () -> service.criarNfse(pedido("250.00"), CHAVE_IDEM));

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test
    void guardaQueRecusaNaoGastaNumeroNemGravaNada() {
        when(idempotencias.findByTenantIdAndIdempotencyKey(TENANT, CHAVE_IDEM)).thenReturn(Optional.empty());
        doThrow(new BusinessException("recusado", HttpStatus.UNPROCESSABLE_ENTITY))
                .when(guardaNfse).validar(eq(TENANT), any(NfseRequestDTO.class));

        assertThrows(BusinessException.class, () -> service.criarNfse(pedido("100.00"), CHAVE_IDEM));

        verify(numeracao, never()).proximoNumero(any(), any(), anyString(), anyString());
        verify(documentos, never()).save(any());
    }

    // ---- apoio ----

    private void prepararCriacao() {
        when(idempotencias.findByTenantIdAndIdempotencyKey(TENANT, CHAVE_IDEM)).thenReturn(Optional.empty());
        ConfiguracaoNfse configuracao = mock(ConfiguracaoNfse.class);
        when(configuracao.getProvedor()).thenReturn(CodigoProvedorNfse.ADN);
        when(resolver.configuracaoDe(TENANT, EMITENTE)).thenReturn(configuracao);
        when(numeracao.proximoNumero(TENANT, EMITENTE, "NFSE", "1")).thenReturn(7L);
        when(documentos.save(any(DocumentoFiscal.class))).thenAnswer(chamada -> chamada.getArgument(0));
    }

    private static NfseRequestDTO pedido(String valor) {
        EnderecoDTO endereco = new EnderecoDTO("Rua das Flores", "100", null, "Centro", "3104007", "Arcos", "MG",
                "35588000", null);
        return new NfseRequestDTO(EMITENTE, "HOMOLOGACAO", "1", LocalDate.of(2026, 10, 3),
                new NfseRequestDTO.Prestador("40152424000196", null, "ARCOS TECNOLOGIA LTDA", endereco, null, "3", "1", "0"),
                null,
                new NfseRequestDTO.Servico("010101", null, "Desenvolvimento de software", "3104007"),
                new NfseRequestDTO.Valores(new BigDecimal(valor), "1", null, "1"));
    }
}
