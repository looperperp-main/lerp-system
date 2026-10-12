package com.l.erp.migracaoservice.services;

import com.l.erp.migracaoservice.domain.ExportBatch;
import com.l.erp.migracaoservice.infra.config.ExportProperties;
import com.l.erp.migracaoservice.repository.ExportBatchRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Faxina das 03:00, timeout de PROCESSING e retry — tudo com repositório mockado. */
class ExportMaintenanceServiceTest {

    private static final Long TENANT = 7L;
    private static final UUID ID = UUID.randomUUID();

    @TempDir
    Path base;

    ExportBatchRepository repo;
    ExportExtractionService extracao;
    SimpleMeterRegistry metricas = new SimpleMeterRegistry();
    ExportMaintenanceService service;

    @BeforeEach
    void setUp() {
        repo = mock(ExportBatchRepository.class);
        extracao = mock(ExportExtractionService.class);
        service = new ExportMaintenanceService(repo, extracao, new ExportProperties(base, 7), metricas);
    }

    private ExportBatch batch(UUID id, int tentativas) {
        return new ExportBatch(id, TENANT, UUID.randomUUID(), "{}", "CSV", "PROCESSING", null, null, tentativas,
                null, null, OffsetDateTime.now(), null, OffsetDateTime.now().minusDays(1), null);
    }

    @Test
    void expirar_apagaOsArquivosMarcaExpiradoEAudita() throws Exception {
        Path dir = Files.createDirectories(base.resolve("7").resolve(ID.toString()));
        Files.writeString(dir.resolve("cadastros.cliente.csv"), "id\r\n");
        when(repo.listarExpirados(anyInt())).thenReturn(List.of(batch(ID, 1)));

        service.expirar();

        assertThat(dir).doesNotExist();
        verify(repo).marcarExpirado(ID);
        verify(repo).registrarAuditoria(ID, TENANT, "EXPIRED", null);
        assertThat(metricas.counter("export_manutencao_total", "acao", "expirado").count()).isEqualTo(1.0);
    }

    @Test
    void expirar_umBatchComErroNaoImpedeOsDemais() {
        UUID outro = UUID.randomUUID();
        when(repo.listarExpirados(anyInt())).thenReturn(List.of(batch(ID, 1), batch(outro, 1)));
        doThrow(new IllegalStateException("banco")).when(repo).marcarExpirado(ID);

        service.expirar();

        verify(repo).marcarExpirado(outro);
        verify(repo, never()).registrarAuditoria(ID, TENANT, "EXPIRED", null);
    }

    @Test
    void recuperar_travadoComTentativaSobrandoVoltaParaAFilaEReexecuta() {
        when(repo.listarTravados(anyInt(), anyInt())).thenReturn(List.of(batch(ID, 1)));
        when(repo.reenfileirarOuFalhar(any(), anyInt(), anyString())).thenReturn(true);

        service.recuperar();

        verify(repo).reenfileirarOuFalhar(ID, 3, "A geração da exportação excedeu o tempo limite. Solicite uma nova exportação");
        verify(extracao).executar(ID, TENANT);
        assertThat(metricas.counter("export_manutencao_total", "acao", "timeout").count()).isEqualTo(1.0);
    }

    @Test
    void recuperar_travadoNaUltimaTentativaFalhaSemReexecutar() {
        when(repo.listarTravados(anyInt(), anyInt())).thenReturn(List.of(batch(ID, 3)));
        when(repo.reenfileirarOuFalhar(any(), anyInt(), anyString())).thenReturn(true);

        service.recuperar();

        verify(extracao, never()).executar(any(), any());
    }

    @Test
    void recuperar_seAExecucaoTerminouEnquantoOlhavamNaoFazNada() {
        when(repo.listarTravados(anyInt(), anyInt())).thenReturn(List.of(batch(ID, 1)));
        when(repo.reenfileirarOuFalhar(any(), anyInt(), anyString())).thenReturn(false);

        service.recuperar();

        verify(extracao, never()).executar(any(), any());
    }

    @Test
    void recuperar_reexecutaPendentesAntigos() {
        when(repo.listarPendentesAntigos(anyInt(), anyInt())).thenReturn(List.of(batch(ID, 1)));

        service.recuperar();

        verify(extracao).executar(ID, TENANT);
        assertThat(metricas.counter("export_manutencao_total", "acao", "reexecutado").count()).isEqualTo(1.0);
    }
}
