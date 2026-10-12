package com.l.erp.migracaoservice.repository;

import com.l.erp.migracaoservice.repository.ImportRowRepository.LinhaComErro;
import com.l.erp.migracaoservice.repository.ImportRowRepository.LinhaNova;
import com.l.erp.migracaoservice.repository.ImportRowRepository.LinhaStaging;
import com.l.erp.migracaoservice.repository.ImportRowRepository.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Sem Postgres: verifica o SQL (tenant e batch sempre no WHERE), os parâmetros e o que cada lote grava por linha. */
class ImportRowRepositoryTest {

    private static final UUID BATCH = UUID.randomUUID();

    private StatementSpec spec;
    private JdbcClient jdbc;
    private JdbcTemplate template;
    private ImportRowRepository repo;

    @BeforeEach
    void setUp() {
        spec = Mockito.mock(StatementSpec.class, Mockito.RETURNS_SELF);
        jdbc = Mockito.mock(JdbcClient.class);
        template = Mockito.mock(JdbcTemplate.class);
        when(jdbc.sql(anyString())).thenReturn(spec);
        repo = new ImportRowRepository(jdbc, template);
    }

    @SuppressWarnings("unchecked")
    private <T> MappedQuerySpec<T> mappedMock() {
        return Mockito.mock(MappedQuerySpec.class);
    }

    private String sqlExecutado() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).sql(sql.capture());
        return sql.getValue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void inserirLoteGravaTenantBatchTrilhaELinhaDeCadaItem() throws SQLException {
        repo.inserirLote(7L, BATCH, "CUSTOMER", "TEMPLATE", List.of(new LinhaNova(2, "{\"a\":1}")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ParameterizedPreparedStatementSetter<LinhaNova>> setter = ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(template).batchUpdate(sql.capture(), ArgumentMatchers.<List<LinhaNova>>any(), eq(1), setter.capture());
        assertThat(sql.getValue()).contains("INSERT INTO migracao.raw_import_row").contains("CAST(? AS jsonb)");

        PreparedStatement ps = Mockito.mock(PreparedStatement.class);
        setter.getValue().setValues(ps, new LinhaNova(2, "{\"a\":1}"));
        verify(ps).setLong(1, 7L);
        verify(ps).setObject(2, BATCH);
        verify(ps).setString(3, "CUSTOMER");
        verify(ps).setInt(4, 2);
        verify(ps).setString(5, "{\"a\":1}");
        verify(ps).setString(6, "TEMPLATE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void gravarResultadosAtualizaPorIdSempreComOTenantNoWhere() throws SQLException {
        repo.gravarResultados(7L, List.of(new Resultado(10, "ERROR", "[{\"field\":\"x\"}]")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ParameterizedPreparedStatementSetter<Resultado>> setter = ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(template).batchUpdate(sql.capture(), ArgumentMatchers.<List<Resultado>>any(), eq(1), setter.capture());
        assertThat(sql.getValue()).contains("WHERE id = ? AND tenant_id = ?");

        PreparedStatement ps = Mockito.mock(PreparedStatement.class);
        setter.getValue().setValues(ps, new Resultado(10, "ERROR", "[{\"field\":\"x\"}]"));
        verify(ps).setString(1, "ERROR");
        verify(ps).setString(2, "[{\"field\":\"x\"}]");
        verify(ps).setLong(3, 10L);
        verify(ps).setLong(4, 7L);
    }

    @Test
    void proximasPaginaPorChaveEFiltraPorBatchETenant() throws SQLException {
        MappedQuerySpec<LinhaStaging> mapped = mappedMock();
        when(spec.query(ArgumentMatchers.<RowMapper<LinhaStaging>>any())).thenReturn(mapped);
        when(mapped.list()).thenReturn(List.of());

        assertThat(repo.proximas(BATCH, 7L, 50L, 1000)).isEmpty();

        assertThat(sqlExecutado()).contains("batch_id = :batch AND tenant_id = :tenant AND id > :depois").contains("ORDER BY id");
        verify(spec).param("depois", 50L);
        verify(spec).param("limite", 1000);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RowMapper<LinhaStaging>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(spec).query(mapper.capture());
        ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(51L);
        when(rs.getInt("row_num")).thenReturn(3);
        when(rs.getString("raw_json")).thenReturn("{}");
        assertThat(mapper.getValue().mapRow(rs, 0)).isEqualTo(new LinhaStaging(51, 3, "{}"));
    }

    @Test
    void proximasValidasFiltraPorStatusValidEPorBatchETenant() throws SQLException {
        MappedQuerySpec<LinhaStaging> mapped = mappedMock();
        when(spec.query(ArgumentMatchers.<RowMapper<LinhaStaging>>any())).thenReturn(mapped);
        when(mapped.list()).thenReturn(List.of());

        assertThat(repo.proximasValidas(BATCH, 7L, 50L, 1000)).isEmpty();

        assertThat(sqlExecutado()).contains("batch_id = :batch AND tenant_id = :tenant AND status = :status AND id > :depois").contains("ORDER BY id");
        verify(spec).param("status", "VALID");                  // linha com erro ou já promovida nunca é regravada
        verify(spec).param("depois", 50L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RowMapper<LinhaStaging>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(spec).query(mapper.capture());
        ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(51L);
        when(rs.getInt("row_num")).thenReturn(3);
        when(rs.getString("raw_json")).thenReturn("{}");
        assertThat(mapper.getValue().mapRow(rs, 0)).isEqualTo(new LinhaStaging(51, 3, "{}"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void gravarPromocaoAtualizaStatusEMotivoPorIdSempreComOTenantNoWhere() throws SQLException {
        repo.gravarPromocao(7L, List.of(new Resultado(10, "SKIPPED", "[{\"field\":\"sku\"}]")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ParameterizedPreparedStatementSetter<Resultado>> setter = ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(template).batchUpdate(sql.capture(), ArgumentMatchers.<List<Resultado>>any(), eq(1), setter.capture());
        assertThat(sql.getValue()).contains("promoted_at = now()").contains("WHERE id = ? AND tenant_id = ?");

        PreparedStatement ps = Mockito.mock(PreparedStatement.class);
        setter.getValue().setValues(ps, new Resultado(10, "SKIPPED", "[{\"field\":\"sku\"}]"));
        verify(ps).setString(1, "SKIPPED");
        verify(ps).setString(2, "[{\"field\":\"sku\"}]");
        verify(ps).setLong(3, 10L);
        verify(ps).setLong(4, 7L);
    }

    @Test
    void purgarLinhasApagaSoDoBatchEDoTenant() {
        when(spec.update()).thenReturn(12);

        assertThat(repo.purgarLinhas(BATCH, 7L)).isEqualTo(12);

        assertThat(sqlExecutado()).contains("DELETE FROM migracao.raw_import_row WHERE batch_id = :batch AND tenant_id = :tenant");
        verify(spec).param("batch", BATCH);
        verify(spec).param("tenant", 7L);
    }

    @Test
    void contarPorStatusSemBatchesNemConsulta() {
        assertThat(repo.contarPorStatus(7L, List.of())).isEmpty();

        verifyNoInteractions(jdbc);
    }

    @Test
    void contarPorStatusAgrupaPorBatchEStatusNumaQuerySo() throws SQLException {
        MappedQuerySpec<Object> mapped = mappedMock();
        when(spec.query(ArgumentMatchers.<RowMapper<Object>>any())).thenReturn(mapped);

        Map<UUID, Map<String, Long>> contagens = repo.contarPorStatus(7L, List.of(BATCH));

        assertThat(sqlExecutado()).contains("tenant_id = :tenant").contains("batch_id IN (:batches)").contains("GROUP BY batch_id, status");
        verify(spec).param("batches", List.of(BATCH));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RowMapper<Object>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(spec).query(mapper.capture());
        ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.getObject("batch_id", UUID.class)).thenReturn(BATCH);
        when(rs.getString("status")).thenReturn("VALID", "ERROR");
        when(rs.getLong("total")).thenReturn(7L, 3L);
        mapper.getValue().mapRow(rs, 0);
        mapper.getValue().mapRow(rs, 1);

        assertThat(contagens).containsEntry(BATCH, Map.of("VALID", 7L, "ERROR", 3L));   // o mapper preenche o mesmo mapa devolvido
    }

    @Test
    void listarErrosFiltraPorStatusErrorEOrdenaPelaLinhaDaPlanilha() throws SQLException {
        MappedQuerySpec<LinhaComErro> mapped = mappedMock();
        when(spec.query(ArgumentMatchers.<RowMapper<LinhaComErro>>any())).thenReturn(mapped);
        when(mapped.list()).thenReturn(List.of());

        assertThat(repo.listarErros(BATCH, 7L, 100, 200)).isEmpty();

        assertThat(sqlExecutado()).contains("batch_id = :batch AND tenant_id = :tenant AND status = :status").contains("ORDER BY row_num");
        verify(spec).param("status", "ERROR");
        verify(spec).param("limite", 100);
        verify(spec).param("deslocamento", 200);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RowMapper<LinhaComErro>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(spec).query(mapper.capture());
        ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.getInt("row_num")).thenReturn(47);
        when(rs.getString("raw_json")).thenReturn("{}");
        when(rs.getString("error_detail")).thenReturn("[]");
        assertThat(mapper.getValue().mapRow(rs, 0)).isEqualTo(new LinhaComErro(47, "{}", "[]"));
    }
}
