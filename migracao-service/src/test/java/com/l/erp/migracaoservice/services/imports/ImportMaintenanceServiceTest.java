package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.infra.config.ImportProperties;
import com.l.erp.migracaoservice.repository.ImportBatchRepository;
import com.l.erp.migracaoservice.repository.ImportBatchRepository.Purgavel;
import com.l.erp.migracaoservice.repository.ImportRowRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportMaintenanceServiceTest {

    private static final Long TENANT = 7L;
    private static final UUID ID = UUID.randomUUID();

    @TempDir
    Path base;

    private ImportBatchRepository batches;
    private ImportRowRepository rows;
    private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();
    private ImportMaintenanceService service;

    @BeforeEach
    void setUp() {
        batches = mock(ImportBatchRepository.class);
        rows = mock(ImportRowRepository.class);
        service = new ImportMaintenanceService(batches, rows, new ImportProperties(base, 30), metricas);
    }

    @Test
    void destravarAplicaOsDoisTimeoutsDaSpecEContaSoOQueMexeu() {
        when(batches.expirarTravados("VALIDATING", 15, Constants.IMPORT_FALHA_TIMEOUT_VALIDACAO)).thenReturn(2);
        when(batches.expirarTravados("PROMOTING", 5, Constants.IMPORT_FALHA_TIMEOUT_PROMOCAO)).thenReturn(0);

        service.destravar();

        assertThat(metricas.counter("import_manutencao_total", "acao", "timeout_validacao").count()).isEqualTo(2.0);
        assertThat(metricas.counter("import_manutencao_total", "acao", "timeout_promocao").count()).isZero();
    }

    @Test
    void purgarApagaOArquivoAsLinhasEMarcaOBatch() throws Exception {
        Path dir = Files.createDirectories(base.resolve("7").resolve(ID.toString()));
        Files.writeString(dir.resolve("original.csv"), "tipo;documento\n");
        when(batches.listarParaPurgar(anyInt())).thenReturn(List.of(new Purgavel(ID, TENANT)));

        service.purgar();

        assertThat(dir).doesNotExist();
        verify(rows).purgarLinhas(ID, TENANT);
        verify(batches).marcarPurgado(ID, TENANT, Constants.IMPORT_FALHA_RETENCAO);
        assertThat(metricas.counter("import_manutencao_total", "acao", "purgado").count()).isEqualTo(1.0);
    }

    @Test
    void purgarComArquivoJaAusenteNaoFalhaEFechaOBatch() {
        when(batches.listarParaPurgar(anyInt())).thenReturn(List.of(new Purgavel(ID, TENANT)));

        service.purgar();                               // o diretório nunca existiu (ex.: upload recusado): segue

        verify(batches).marcarPurgado(ID, TENANT, Constants.IMPORT_FALHA_RETENCAO);
    }

    @Test
    void umBatchComErroNaoImpedeOsDemaisNemFicaMarcadoComoPurgado() {
        UUID outro = UUID.randomUUID();
        when(batches.listarParaPurgar(anyInt())).thenReturn(List.of(new Purgavel(ID, TENANT), new Purgavel(outro, TENANT)));
        doThrow(new IllegalStateException("banco indisponível")).when(rows).purgarLinhas(ID, TENANT);

        service.purgar();

        verify(batches, never()).marcarPurgado(ID, TENANT, Constants.IMPORT_FALHA_RETENCAO);   // tenta de novo na próxima rodada
        verify(batches).marcarPurgado(outro, TENANT, Constants.IMPORT_FALHA_RETENCAO);
    }
}
