package com.l.erp.migracaoservice.repository;

import com.l.erp.migracaoservice.domain.ExportBatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sem Postgres: o StatementSpec devolve a si mesmo em cada .param(), e o que se verifica é o SQL
 * (tenant sempre no WHERE), os parâmetros e o mapeamento da linha. O SQL de verdade é validado ao
 * subir contra o banco / teste de integração.
 */
class ExportBatchRepositoryTest {

    private static final UUID ID = UUID.randomUUID();

    private StatementSpec spec;
    private JdbcClient jdbc;
    private ExportBatchRepository repo;

    @BeforeEach
    void setUp() {
        spec = Mockito.mock(StatementSpec.class, Mockito.RETURNS_SELF);
        jdbc = Mockito.mock(JdbcClient.class);
        when(jdbc.sql(anyString())).thenReturn(spec);
        repo = new ExportBatchRepository(jdbc);
    }

    @SuppressWarnings("unchecked")
    private <T> MappedQuerySpec<T> mappedMock() {
        return Mockito.mock(MappedQuerySpec.class);
    }

    @Test
    void inserirGravaOBatchComTenantEHash() {
        repo.inserir(ID, 7L, ID, "{}", "h", "CSV", null);

        verify(spec).param("tenant", 7L);
        verify(spec).param("hash", "h");
        verify(spec).update();
    }

    @Test
    void buscarFiltraPorIdETenant() {
        MappedQuerySpec<ExportBatch> mapped = mappedMock();
        when(spec.query(ArgumentMatchers.<RowMapper<ExportBatch>>any())).thenReturn(mapped);
        when(mapped.optional()).thenReturn(Optional.empty());

        assertThat(repo.buscar(ID, 7L)).isEmpty();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).sql(sql.capture());
        assertThat(sql.getValue()).contains("WHERE id = :id AND tenant_id = :tenant");
        verify(spec).param("tenant", 7L);
    }

    @Test
    void buscarProntoPorEscopoExigeReadyDentroDaValidade() {
        MappedQuerySpec<ExportBatch> mapped = mappedMock();
        when(spec.query(ArgumentMatchers.<RowMapper<ExportBatch>>any())).thenReturn(mapped);
        when(mapped.optional()).thenReturn(Optional.empty());

        repo.buscarProntoPorEscopo(7L, "h");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).sql(sql.capture());
        assertThat(sql.getValue()).contains("tenant_id = :tenant").contains("scope_hash = :hash").contains("expires_at > now()");
        verify(spec).param("status", "READY");
    }

    @Test
    void listarEContarSempreFiltramPorTenant() {
        MappedQuerySpec<ExportBatch> mapped = mappedMock();
        when(spec.query(ArgumentMatchers.<RowMapper<ExportBatch>>any())).thenReturn(mapped);
        when(mapped.list()).thenReturn(List.of());
        MappedQuerySpec<Long> contagem = mappedMock();
        when(spec.query(eq(Long.class))).thenReturn(contagem);
        when(contagem.single()).thenReturn(3L);

        assertThat(repo.listar(7L, 20, 40)).isEmpty();
        assertThat(repo.contar(7L)).isEqualTo(3L);

        verify(spec, Mockito.times(2)).param("tenant", 7L);
        verify(spec).param("limite", 20);
        verify(spec).param("deslocamento", 40);
    }

    @Test
    void marcarProcessandoDevolveFalseSeOutraThreadJaPegou() {
        when(spec.update()).thenReturn(1, 0);

        assertThat(repo.marcarProcessando(ID)).isTrue();
        assertThat(repo.marcarProcessando(ID)).isFalse();
        verify(spec, Mockito.times(2)).param("de", "REQUESTED");
    }

    @Test
    void transicoesDeEstadoGravamStatusEDados() {
        repo.marcarPronto(ID, "{}", "{}", "tok", OffsetDateTime.now());
        verify(spec).param("status", "READY");
        verify(spec).param("token", "tok");

        repo.marcarFalhou(ID, "motivo");
        verify(spec).param("status", "FAILED");
        verify(spec).param("motivo", "motivo");
    }

    @Test
    void buscarPorTokenFiltraSoPeloToken() {
        MappedQuerySpec<ExportBatch> mapped = mappedMock();
        when(spec.query(ArgumentMatchers.<RowMapper<ExportBatch>>any())).thenReturn(mapped);
        when(mapped.optional()).thenReturn(Optional.empty());

        assertThat(repo.buscarPorToken("tok")).isEmpty();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).sql(sql.capture());
        assertThat(sql.getValue()).contains("WHERE download_token = :token");
        verify(spec).param("token", "tok");
    }

    @Test
    void marcarBaixadoPreservaAPrimeiraDataDeDownload() {
        repo.marcarBaixado(ID);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).sql(sql.capture());
        assertThat(sql.getValue()).contains("COALESCE(downloaded_at, now())").contains("status IN (:ready, :para)");
        verify(spec).param("para", "DOWNLOADED");
        verify(spec).param("ready", "READY");
        verify(spec).update();
    }

    @Test
    void reenfileirarOuFalharSoAgeEmProcessingEDecidePelaTentativa() {
        when(spec.update()).thenReturn(1, 0);

        assertThat(repo.reenfileirarOuFalhar(ID, 3, "m")).isTrue();
        assertThat(repo.reenfileirarOuFalhar(ID, 3, "m")).isFalse();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, Mockito.times(2)).sql(sql.capture());
        assertThat(sql.getValue()).contains("attempt_count < :max").contains("AND status = :processing");
        verify(spec, Mockito.times(2)).param("max", 3);
        verify(spec, Mockito.times(2)).param("processing", "PROCESSING");
    }

    @Test
    void listagensDeManutencaoFiltramPorStatusEJanela() {
        MappedQuerySpec<ExportBatch> mapped = mappedMock();
        when(spec.query(ArgumentMatchers.<RowMapper<ExportBatch>>any())).thenReturn(mapped);
        when(mapped.list()).thenReturn(List.of());

        repo.listarExpirados(100);
        repo.listarTravados(30, 100);
        repo.listarPendentesAntigos(5, 100);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, Mockito.times(3)).sql(sql.capture());
        assertThat(sql.getAllValues().get(0)).contains("expires_at <= now()");
        assertThat(sql.getAllValues().get(1)).contains("started_at < now() - make_interval(mins => :minutos)");
        assertThat(sql.getAllValues().get(2)).contains("COALESCE(started_at, created_at)");
        verify(spec).param("status", "PROCESSING");
        verify(spec).param("status", "REQUESTED");
        verify(spec).param("minutos", 30);
        verify(spec).param("minutos", 5);
    }

    @Test
    void adminMontaOWhereSoComOsFiltrosInformados() {
        MappedQuerySpec<ExportBatch> mapped = mappedMock();
        when(spec.query(ArgumentMatchers.<RowMapper<ExportBatch>>any())).thenReturn(mapped);
        when(mapped.list()).thenReturn(List.of());

        repo.listarAdmin(null, null, 20, 0);
        repo.listarAdmin(7L, null, 20, 0);
        repo.listarAdmin(null, "FAILED", 20, 0);
        repo.listarAdmin(7L, "FAILED", 20, 0);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, Mockito.times(4)).sql(sql.capture());
        assertThat(sql.getAllValues().get(0)).doesNotContain("WHERE");
        assertThat(sql.getAllValues().get(1)).contains("WHERE tenant_id = :tenant").doesNotContain("status = :status");
        assertThat(sql.getAllValues().get(2)).contains("WHERE status = :status").doesNotContain("tenant_id = :tenant");
        assertThat(sql.getAllValues().get(3)).contains("WHERE tenant_id = :tenant AND status = :status");
        // tenant só vira parâmetro nas duas chamadas que o informaram
        verify(spec, Mockito.times(2)).param("tenant", 7L);
        verify(spec, Mockito.times(2)).param("status", "FAILED");
    }

    @Test
    void adminContaComOsMesmosFiltros() {
        MappedQuerySpec<Long> contagem = mappedMock();
        when(spec.query(eq(Long.class))).thenReturn(contagem);
        when(contagem.single()).thenReturn(9L);

        assertThat(repo.contarAdmin(7L, "READY")).isEqualTo(9L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).sql(sql.capture());
        assertThat(sql.getValue()).startsWith("SELECT count(*)").contains("WHERE tenant_id = :tenant AND status = :status");
    }

    @Test
    void adminBuscaPorIdSemFiltroDeTenant() {
        MappedQuerySpec<ExportBatch> mapped = mappedMock();
        when(spec.query(ArgumentMatchers.<RowMapper<ExportBatch>>any())).thenReturn(mapped);
        when(mapped.optional()).thenReturn(Optional.empty());

        assertThat(repo.buscarDeQualquerTenant(ID)).isEmpty();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).sql(sql.capture());
        assertThat(sql.getValue()).contains("WHERE id = :id").doesNotContain("tenant_id = :tenant");
    }

    @Test
    void marcarExpiradoSoAgeEmReadyOuDownloaded() {
        repo.marcarExpirado(ID);

        verify(spec).param("status", "EXPIRED");
        verify(spec).param("downloaded", "DOWNLOADED");
        verify(spec).update();
    }

    @Test
    void auditoriaGravaAcaoETenant() {
        repo.registrarAuditoria(ID, 7L, "GENERATED", null);

        verify(spec).param("acao", "GENERATED");
        verify(spec).param("tenant", 7L);
        verify(spec).update();
    }

    @Test
    void listarAuditoriaFiltraPorBatchETenantEMapeiaALinha() throws Exception {
        MappedQuerySpec<ExportBatchRepository.AuditoriaLinha> mapped = mappedMock();
        when(spec.query(ArgumentMatchers.<RowMapper<ExportBatchRepository.AuditoriaLinha>>any())).thenReturn(mapped);
        when(mapped.list()).thenReturn(List.of());

        repo.listarAuditoria(ID, 7L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RowMapper<ExportBatchRepository.AuditoriaLinha>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(spec).query(mapper.capture());
        verify(spec).param("tenant", 7L);
        ResultSet rs = Mockito.mock(ResultSet.class);
        OffsetDateTime quando = OffsetDateTime.now();
        when(rs.getString("action")).thenReturn("DOWNLOADED");
        when(rs.getObject("actor_id", UUID.class)).thenReturn(ID);
        when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(quando);

        var linha = mapper.getValue().mapRow(rs, 0);

        assertThat(linha.acao()).isEqualTo("DOWNLOADED");
        assertThat(linha.ator()).isEqualTo(ID);
        assertThat(linha.em()).isEqualTo(quando);
    }

    @Test
    void mapeiaAsColunasDoBatch() throws Exception {
        MappedQuerySpec<ExportBatch> mapped = mappedMock();
        when(spec.query(ArgumentMatchers.<RowMapper<ExportBatch>>any())).thenReturn(mapped);
        when(mapped.optional()).thenReturn(Optional.empty());
        repo.buscar(ID, 7L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<RowMapper<ExportBatch>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(spec).query(mapper.capture());

        ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(ID);
        when(rs.getLong("tenant_id")).thenReturn(7L);
        when(rs.getString("scope")).thenReturn("{}");
        when(rs.getString("format")).thenReturn("CSV");
        when(rs.getString("status")).thenReturn("READY");
        when(rs.getInt("attempt_count")).thenReturn(1);

        ExportBatch b = mapper.getValue().mapRow(rs, 0);

        assertThat(b.id()).isEqualTo(ID);
        assertThat(b.tenantId()).isEqualTo(7L);
        assertThat(b.status()).isEqualTo("READY");
        assertThat(b.attemptCount()).isEqualTo(1);
    }
}
