package com.l.erp.migracaoservice.repository;

import com.l.erp.migracaoservice.domain.ImportBatch;
import com.l.erp.migracaoservice.repository.ImportBatchRepository.Purgavel;
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
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sem Postgres: o StatementSpec devolve a si mesmo em cada .param(); verifica-se o SQL (tenant sempre no WHERE) e os parâmetros. */
class ImportBatchRepositoryTest {

    private static final UUID ID = UUID.randomUUID();

    private StatementSpec spec;
    private JdbcClient jdbc;
    private ImportBatchRepository repo;

    @BeforeEach
    void setUp() {
        spec = Mockito.mock(StatementSpec.class, Mockito.RETURNS_SELF);
        jdbc = Mockito.mock(JdbcClient.class);
        when(jdbc.sql(anyString())).thenReturn(spec);
        repo = new ImportBatchRepository(jdbc);
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
    void inserirGravaOBatchComTenantTrilhaECaminhoRelativo() {
        OffsetDateTime purgar = OffsetDateTime.now().plusDays(30);

        repo.inserir(ID, 7L, ID, "CUSTOMER", "1.0", "TEMPLATE", "clientes.csv", "7/x/original.csv", purgar);

        verify(spec).param("tenant", 7L);
        verify(spec).param("trilha", "TEMPLATE");
        verify(spec).param("caminho", "7/x/original.csv");
        verify(spec).param("purgar", purgar);
        verify(spec).update();
    }

    @Test
    void buscarFiltraPorIdETenantEMapeiaALinha() throws SQLException {
        MappedQuerySpec<ImportBatch> mapped = mappedMock();
        when(spec.query(ArgumentMatchers.<RowMapper<ImportBatch>>any())).thenReturn(mapped);
        when(mapped.optional()).thenReturn(Optional.empty());

        assertThat(repo.buscar(ID, 7L)).isEmpty();

        assertThat(sqlExecutado()).contains("WHERE id = :id AND tenant_id = :tenant");
        verify(spec).param("tenant", 7L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RowMapper<ImportBatch>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(spec).query(mapper.capture());
        ResultSet rs = Mockito.mock(ResultSet.class);
        OffsetDateTime agora = OffsetDateTime.now();
        when(rs.getObject("id", UUID.class)).thenReturn(ID);
        when(rs.getLong("tenant_id")).thenReturn(7L);
        when(rs.getObject("requested_by", UUID.class)).thenReturn(ID);
        when(rs.getString("entity_type")).thenReturn("CUSTOMER");
        when(rs.getString("schema_version")).thenReturn("1.0");
        when(rs.getString("status")).thenReturn("READY");
        when(rs.getString("source_track")).thenReturn("TEMPLATE");
        when(rs.getString("original_file_name")).thenReturn("clientes.csv");
        when(rs.getString("raw_file_path")).thenReturn("7/x/original.csv");
        when(rs.getObject("row_count")).thenReturn(3);
        when(rs.getString("failure_reason")).thenReturn(null);
        when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(agora);
        when(rs.getObject("updated_at", OffsetDateTime.class)).thenReturn(agora);

        ImportBatch b = mapper.getValue().mapRow(rs, 0);

        assertThat(b).isEqualTo(new ImportBatch(ID, 7L, ID, "CUSTOMER", "1.0", "READY", "TEMPLATE", "clientes.csv",
                "7/x/original.csv", 3, null, agora, agora));
    }

    @Test
    void listarEContarSempreFiltramPorTenant() {
        MappedQuerySpec<ImportBatch> mapped = mappedMock();
        when(spec.query(ArgumentMatchers.<RowMapper<ImportBatch>>any())).thenReturn(mapped);
        when(mapped.list()).thenReturn(List.of());
        MappedQuerySpec<Long> contagem = mappedMock();
        when(spec.query(ArgumentMatchers.eq(Long.class))).thenReturn(contagem);
        when(contagem.single()).thenReturn(3L);

        assertThat(repo.listar(7L, 20, 40)).isEmpty();
        assertThat(repo.contar(7L)).isEqualTo(3L);

        verify(spec, times(2)).param("tenant", 7L);
        verify(spec).param("limite", 20);
        verify(spec).param("deslocamento", 40);
    }

    @Test
    void iniciarValidacaoDevolveFalseSeOutraThreadJaPegouOBatch() {
        when(spec.update()).thenReturn(1, 0);

        assertThat(repo.iniciarValidacao(ID, 7L)).isTrue();
        assertThat(repo.iniciarValidacao(ID, 7L)).isFalse();

        verify(spec, times(2)).param("para", "VALIDATING");
        verify(spec, times(2)).param("uploaded", "UPLOADED");
        verify(spec, times(2)).param("awaiting", "AWAITING_FIX");
    }

    @Test
    void concluirValidacaoSoAgeEmValidatingEFiltraPorTenant() {
        repo.concluirValidacao(ID, 7L, "READY");

        assertThat(sqlExecutado()).contains("tenant_id = :tenant").contains("status = :de");
        verify(spec).param("para", "READY");
        verify(spec).param("de", "VALIDATING");
        verify(spec).update();
    }

    @Test
    void falharGravaFailedETruncaOMotivoNaColuna() {
        repo.falhar(ID, 7L, "x".repeat(450));

        verify(spec).param("status", "FAILED");
        verify(spec).param("motivo", "x".repeat(300));
        assertThat(sqlExecutado()).contains("tenant_id = :tenant");
    }

    @Test
    void falharComMotivoNuloNaoQuebra() {
        repo.falhar(ID, 7L, null);

        verify(spec).param("motivo", (Object) null);
    }

    @Test
    void iniciarPromocaoSoPegaBatchReadyEDevolveFalseSeOutraRequisicaoJaPegou() {
        when(spec.update()).thenReturn(1, 0);

        assertThat(repo.iniciarPromocao(ID, 7L)).isTrue();
        assertThat(repo.iniciarPromocao(ID, 7L)).isFalse();         // duplo clique / segunda aba

        verify(spec, times(2)).param("para", "PROMOTING");
        verify(spec, times(2)).param("de", "READY");
        verify(spec, times(2)).param("tenant", 7L);
    }

    @Test
    void concluirPromocaoSoAgeEmPromotingEReiniciaARetencaoDoStaging() {
        when(spec.update()).thenReturn(1);

        assertThat(repo.concluirPromocao(ID, 7L, 30)).isTrue();

        assertThat(sqlExecutado()).contains("purge_after").contains("tenant_id = :tenant").contains("status = :de");
        verify(spec).param("para", "DONE");
        verify(spec).param("de", "PROMOTING");
        verify(spec).param("dias", 30);
    }

    @Test
    void concluirPromocaoDevolveFalseSeOTimeoutJaFechouOBatch() {
        when(spec.update()).thenReturn(0);

        assertThat(repo.concluirPromocao(ID, 7L, 30)).isFalse();    // o chamador desfaz a transação do destino
    }

    @Test
    void expirarTravadosFiltraPorEstadoEPrazoEDevolveQuantos() {
        when(spec.update()).thenReturn(3);

        assertThat(repo.expirarTravados("VALIDATING", 15, "Timeout")).isEqualTo(3);

        assertThat(sqlExecutado()).contains("status = :status").contains("updated_at < now()");
        verify(spec).param("falhou", "FAILED");
        verify(spec).param("motivo", "Timeout");
        verify(spec).param("status", "VALIDATING");
        verify(spec).param("minutos", 15);
    }

    @Test
    void listarParaPurgarIgnoraBatchEmProcessamentoEMapeiaIdETenant() throws SQLException {
        MappedQuerySpec<Purgavel> mapped = mappedMock();
        when(spec.query(ArgumentMatchers.<RowMapper<Purgavel>>any())).thenReturn(mapped);
        when(mapped.list()).thenReturn(List.of());

        assertThat(repo.listarParaPurgar(100)).isEmpty();

        assertThat(sqlExecutado()).contains("purged_at IS NULL").contains("purge_after < now()").contains("NOT IN (:validando, :promovendo)");
        verify(spec).param("validando", "VALIDATING");
        verify(spec).param("promovendo", "PROMOTING");
        verify(spec).param("limite", 100);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RowMapper<Purgavel>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(spec).query(mapper.capture());
        ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(ID);
        when(rs.getLong("tenant_id")).thenReturn(7L);
        assertThat(mapper.getValue().mapRow(rs, 0)).isEqualTo(new Purgavel(ID, 7L));
    }

    @Test
    void marcarPurgadoPreservaDoneEFailedEFiltraPorTenant() {
        repo.marcarPurgado(ID, 7L, "descartado");

        assertThat(sqlExecutado()).contains("purged_at = now()").contains("tenant_id = :tenant").contains("CASE WHEN status IN (:done, :falhou)");
        verify(spec).param("done", "DONE");
        verify(spec).param("falhou", "FAILED");
        verify(spec).param("motivo", "descartado");
        verify(spec).update();
    }

    @Test
    void definirTotalFiltraPorTenant() {
        repo.definirTotal(ID, 7L, 2);

        assertThat(sqlExecutado()).contains("row_count = :linhas").contains("tenant_id = :tenant");
        verify(spec).param("linhas", 2);
        verify(spec).update();
    }
}
