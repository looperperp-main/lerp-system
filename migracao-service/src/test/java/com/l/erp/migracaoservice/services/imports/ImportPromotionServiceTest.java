package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.domain.ImportBatch;
import com.l.erp.migracaoservice.infra.config.ImportProperties;
import com.l.erp.migracaoservice.repository.ImportBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A transação é simulada (executa o callback na hora): o que se prova aqui é a ordem e o que acontece em cada falha. */
class ImportPromotionServiceTest {

    private static final Long TENANT = 7L;
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ID = UUID.randomUUID();

    private ImportBatchRepository batches;
    private Promotor promotor;
    private ImportPromotionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        batches = mock(ImportBatchRepository.class);
        promotor = mock(Promotor.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(promotor.entidade()).thenReturn("CUSTOMER");
        when(batches.buscar(ID, TENANT)).thenReturn(Optional.of(new ImportBatch(ID, TENANT, USER, "CUSTOMER", "1.0",
                "PROMOTING", "TEMPLATE", "c.csv", "7/x/original.csv", 2, null, OffsetDateTime.now(), OffsetDateTime.now())));
        doAnswer(inv -> {
            ((Consumer<Object>) inv.getArgument(0)).accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());
        service = new ImportPromotionService(batches, List.of(promotor), tx, new ImportProperties(Path.of("x"), 30));
    }

    @Test
    void promoveEMarcaDoneNaMesmaTransacaoDepoisDeGravarODestino() {
        when(batches.concluirPromocao(ID, TENANT, 30)).thenReturn(true);

        service.promover(ID, TENANT, USER);

        var ordem = inOrder(promotor, batches);
        ordem.verify(promotor).promover(TENANT, USER, ID);
        ordem.verify(batches).concluirPromocao(ID, TENANT, 30);
        verify(batches, never()).falhar(any(), any(), any());
    }

    @Test
    void falhaNoDestinoMarcaFailedComMensagemAoClienteSemMarcarDone() {
        doThrow(new IllegalStateException("violou unique")).when(promotor).promover(TENANT, USER, ID);

        service.promover(ID, TENANT, USER);              // não propaga: o @Async engoliria, então o batch é fechado aqui

        verify(batches).falhar(ID, TENANT, Constants.IMPORT_FALHA_PROMOCAO);
        verify(batches, never()).concluirPromocao(any(), any(), anyInt());
    }

    @Test
    void timeoutQueJaMarcouFailedDesfazOBatchEmVezDeDeixarODestinoGravado() {
        when(batches.concluirPromocao(ID, TENANT, 30)).thenReturn(false);

        service.promover(ID, TENANT, USER);

        // A exceção dentro do callback é o que faz a transação real dar rollback no destino.
        verify(batches).falhar(ID, TENANT, Constants.IMPORT_FALHA_PROMOCAO);
    }

    @Test
    void entidadeSemPromotorFalhaOBatchEmVezDeFicarPresoEmPromoting() {
        when(promotor.entidade()).thenReturn("OUTRA");

        service.promover(ID, TENANT, USER);

        verify(batches).falhar(ID, TENANT, Constants.IMPORT_FALHA_PROMOCAO);
        verify(promotor, never()).promover(any(), any(), any());
    }

    @Test
    void batchInexistenteTambemFechaComFalha() {
        when(batches.buscar(ID, TENANT)).thenReturn(Optional.empty());

        service.promover(ID, TENANT, USER);

        verify(batches).falhar(eq(ID), eq(TENANT), any());
    }
}
