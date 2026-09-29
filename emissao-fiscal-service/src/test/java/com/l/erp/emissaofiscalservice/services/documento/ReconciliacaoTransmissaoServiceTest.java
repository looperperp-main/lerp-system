package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.DocumentoFiscalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** TRANSMITIDO sem desfecho além da janela vai pra ERRO e avisa; dentro da janela ou já resolvido, ninguém mexe. */
@ExtendWith(MockitoExtension.class)
class ReconciliacaoTransmissaoServiceTest {

    private static final UUID ID = UUID.randomUUID();

    @Mock
    private DocumentoFiscalRepository repository;
    @Mock
    private DocumentoFiscalService documentoService;
    @Mock
    private AlertaOperacionalService alerta;

    @Test
    void documentoPresoAlemDaJanelaVaiParaErroEAvisa() {
        var service = new ReconciliacaoTransmissaoService(repository, documentoService, alerta);
        DocumentoFiscal documento = transmitidoDesde(OffsetDateTime.now().minusHours(2));
        when(repository.buscarPorIdEStatusComLock(ID, StatusDocumentoFiscal.TRANSMITIDO)).thenReturn(Optional.of(documento));

        assertTrue(service.encerrarSeAindaPreso(ID, OffsetDateTime.now().minusMinutes(30)));

        assertEquals(Constants.EMISSAO_ERRO_TRANSMITIDO_SEM_DESFECHO, documento.getUltimoErro());
        verify(documentoService).aplicarTransicao(documento, StatusDocumentoFiscal.ERRO);
        verify(alerta).alertarDocumentoEmErro(documento);
    }

    @Test
    void documentoQueAndouDesdeAListagemNaoEhEncerrado() {
        var service = new ReconciliacaoTransmissaoService(repository, documentoService, alerta);
        DocumentoFiscal documento = transmitidoDesde(OffsetDateTime.now());
        when(repository.buscarPorIdEStatusComLock(ID, StatusDocumentoFiscal.TRANSMITIDO)).thenReturn(Optional.of(documento));

        assertFalse(service.encerrarSeAindaPreso(ID, OffsetDateTime.now().minusMinutes(30)));

        verifyNoInteractions(documentoService, alerta);
    }

    @Test
    void documentoQueJaNaoEstaTransmitidoNaoEhTocado() {
        var service = new ReconciliacaoTransmissaoService(repository, documentoService, alerta);
        when(repository.buscarPorIdEStatusComLock(ID, StatusDocumentoFiscal.TRANSMITIDO)).thenReturn(Optional.empty());

        assertFalse(service.encerrarSeAindaPreso(ID, OffsetDateTime.now()));

        verifyNoInteractions(documentoService, alerta);
    }

    private static DocumentoFiscal transmitidoDesde(OffsetDateTime desde) {
        DocumentoFiscal d = new DocumentoFiscal();
        d.setStatus(StatusDocumentoFiscal.TRANSMITIDO);
        d.setUpdatedAt(desde);
        return d;
    }
}
