package com.l.erp.emissaofiscalservice.services.credenciamento;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.domain.AmbienteEmissao;
import com.l.erp.emissaofiscalservice.domain.CredenciamentoSefaz;
import com.l.erp.emissaofiscalservice.domain.StatusCredenciamento;
import com.l.erp.emissaofiscalservice.domain.TipoDocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.CredenciamentoSefazRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Gate de produção do spec §3 item 11: "sem linha CREDENCIADO, emissão em produção nega... nunca
 * tenta e falha na SEFAZ". Homologação nunca exige credenciamento.
 */
@ExtendWith(MockitoExtension.class)
class CredenciamentoSefazServiceTest {

    private static final UUID EMITENTE_ID = UUID.randomUUID();

    @Mock
    private CredenciamentoSefazRepository repository;

    private CredenciamentoSefazService service;

    @BeforeEach
    void setUp() {
        service = new CredenciamentoSefazService(repository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(Constants.HEADER_TENANT_ID, "1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void limparContexto() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void naoExigeCredenciamentoEmHomologacao() {
        assertDoesNotThrow(() -> service.exigirCredenciadoParaProducao(
                EMITENTE_ID, "RJ", TipoDocumentoFiscal.NFE, AmbienteEmissao.HOMOLOGACAO));
        verifyNoInteractions(repository);
    }

    @Test
    void bloqueiaProducaoSemNenhumaLinhaDeCredenciamento() {
        when(repository.findByTenantIdAndEmitenteIdAndUfAndModelo(
                eq(1L), eq(EMITENTE_ID), eq("RJ"), eq(TipoDocumentoFiscal.NFE)))
                .thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.exigirCredenciadoParaProducao(
                EMITENTE_ID, "RJ", TipoDocumentoFiscal.NFE, AmbienteEmissao.PRODUCAO));
    }

    @Test
    void bloqueiaProducaoQuandoStatusNaoECredenciado() {
        CredenciamentoSefaz pendente = new CredenciamentoSefaz();
        pendente.setStatus(StatusCredenciamento.PENDENTE);
        when(repository.findByTenantIdAndEmitenteIdAndUfAndModelo(any(), any(), any(), any()))
                .thenReturn(Optional.of(pendente));

        assertThrows(BusinessException.class, () -> service.exigirCredenciadoParaProducao(
                EMITENTE_ID, "RJ", TipoDocumentoFiscal.NFE, AmbienteEmissao.PRODUCAO));
    }

    @Test
    void permiteProducaoQuandoCredenciado() {
        CredenciamentoSefaz credenciado = new CredenciamentoSefaz();
        credenciado.setStatus(StatusCredenciamento.CREDENCIADO);
        when(repository.findByTenantIdAndEmitenteIdAndUfAndModelo(any(), any(), any(), any()))
                .thenReturn(Optional.of(credenciado));

        assertDoesNotThrow(() -> service.exigirCredenciadoParaProducao(
                EMITENTE_ID, "RJ", TipoDocumentoFiscal.NFE, AmbienteEmissao.PRODUCAO));
    }
}
