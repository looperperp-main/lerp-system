package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.DocumentoFiscalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Limite de tentativas do passo de assinatura (spec §3 item 10): o job não tenta para sempre. Erro de
 * negócio vai pra ERRO na 1ª falha e avisa; erro de sistema é retentado com backoff até o 5º e então
 * vai pra ERRO e avisa.
 */
@ExtendWith(MockitoExtension.class)
class FalhaAssinaturaServiceTest {

    private static final UUID ID = UUID.randomUUID();

    @Mock
    private DocumentoFiscalRepository repository;
    @Mock
    private DocumentoFiscalService documentoService;
    @Mock
    private AlertaOperacionalService alerta;

    private FalhaAssinaturaService service;

    @BeforeEach
    void setUp() {
        service = new FalhaAssinaturaService(repository, documentoService, alerta);
    }

    @Test
    void erroDeNegocioVaiParaErroNaPrimeiraFalhaEAvisa() {
        DocumentoFiscal documento = rascunhoComTentativas(0);
        when(repository.buscarPorIdEStatusComLock(ID, StatusDocumentoFiscal.RASCUNHO)).thenReturn(Optional.of(documento));

        var status = service.registrar(ID, new BusinessException(Constants.EMISSAO_ERRO_CERTIFICADO_AUSENTE,
                HttpStatus.UNPROCESSABLE_ENTITY));

        assertEquals(StatusDocumentoFiscal.ERRO, status);
        assertEquals(Constants.EMISSAO_ERRO_CERTIFICADO_AUSENTE, documento.getUltimoErro());
        assertEquals(1, documento.getTentativasAssinatura());
        assertNull(documento.getProximaTentativaEm());
        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.ERRO);
        verify(alerta).alertarDocumentoEmErro(documento);
    }

    @Test
    void erroDeSistemaAgendaNovaTentativaComBackoffENaoAvisa() {
        DocumentoFiscal documento = rascunhoComTentativas(0);
        when(repository.buscarPorIdEStatusComLock(ID, StatusDocumentoFiscal.RASCUNHO)).thenReturn(Optional.of(documento));
        OffsetDateTime antes = OffsetDateTime.now();

        var status = service.registrar(ID, new IllegalStateException("banco fora do ar"));

        assertEquals(StatusDocumentoFiscal.RASCUNHO, status);
        assertEquals(1, documento.getTentativasAssinatura());
        assertNotNull(documento.getProximaTentativaEm());
        assertTrue(documento.getProximaTentativaEm().isAfter(antes.plusSeconds(9)), "1ª espera é de 10s");
        verify(repository).save(documento);
        verifyNoInteractions(alerta);
        verify(documentoService, never()).aplicarTransicao(any(), any());
    }

    @Test
    void mensagemDeErroDeSistemaNuncaVazaODetalheTecnico() {
        DocumentoFiscal documento = rascunhoComTentativas(0);
        when(repository.buscarPorIdEStatusComLock(ID, StatusDocumentoFiscal.RASCUNHO)).thenReturn(Optional.of(documento));

        service.registrar(ID, new IllegalStateException("jdbc:postgresql://host-interno:5432 senha=abc"));

        assertEquals(Constants.EMISSAO_ERRO_ASSINATURA_INTERNO, documento.getUltimoErro());
    }

    @Test
    void quintaFalhaDeSistemaVaiParaErroEAvisa() {
        DocumentoFiscal documento = rascunhoComTentativas(Constants.EMISSAO_ASSINATURA_MAX_TENTATIVAS - 1);
        when(repository.buscarPorIdEStatusComLock(ID, StatusDocumentoFiscal.RASCUNHO)).thenReturn(Optional.of(documento));

        var status = service.registrar(ID, new IllegalStateException("kek indisponível"));

        assertEquals(StatusDocumentoFiscal.ERRO, status);
        assertEquals(Constants.EMISSAO_ASSINATURA_MAX_TENTATIVAS, documento.getTentativasAssinatura());
        assertNull(documento.getProximaTentativaEm());
        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.ERRO);
        verify(alerta).alertarDocumentoEmErro(documento);
    }

    @Test
    void quartaFalhaDeSistemaAindaRetenta() {
        DocumentoFiscal documento = rascunhoComTentativas(Constants.EMISSAO_ASSINATURA_MAX_TENTATIVAS - 2);
        when(repository.buscarPorIdEStatusComLock(ID, StatusDocumentoFiscal.RASCUNHO)).thenReturn(Optional.of(documento));

        var status = service.registrar(ID, new IllegalStateException("x"));

        assertEquals(StatusDocumentoFiscal.RASCUNHO, status);
        verifyNoInteractions(alerta);
    }

    @Test
    void backoffDobraACadaTentativa() {
        assertEquals(10, FalhaAssinaturaService.atrasoEmSegundos(1));
        assertEquals(20, FalhaAssinaturaService.atrasoEmSegundos(2));
        assertEquals(40, FalhaAssinaturaService.atrasoEmSegundos(3));
        assertEquals(80, FalhaAssinaturaService.atrasoEmSegundos(4));
    }

    @Test
    void documentoQueJaNaoEstaEmRascunhoNaoEhTocado() {
        when(repository.buscarPorIdEStatusComLock(eq(ID), eq(StatusDocumentoFiscal.RASCUNHO))).thenReturn(Optional.empty());

        assertNull(service.registrar(ID, new IllegalStateException("x")));

        verifyNoInteractions(documentoService, alerta);
    }

    private static DocumentoFiscal rascunhoComTentativas(int tentativas) {
        DocumentoFiscal d = new DocumentoFiscal();
        d.setStatus(StatusDocumentoFiscal.RASCUNHO);
        d.setTentativasAssinatura((short) tentativas);
        return d;
    }
}
