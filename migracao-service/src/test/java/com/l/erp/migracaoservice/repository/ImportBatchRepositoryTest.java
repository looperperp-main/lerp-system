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
    void aguardarMapeamentoSoAgeEmUploadedEMarcaATrilhaSelfMapped() {
        repo.aguardarMapeamento(ID, 7L);

        assertThat(sqlExecutado()).contains("tenant_id = :tenant").contains("status = :de");
        verify(spec).param("para", "AWAITING_MAPPING");
        verify(spec).param("trilha", "SELF_MAPPED");
        verify(spec).param("de", "UPLOADED");
        verify(spec).update();
    }

    @Test
    void iniciarMapeamentoGravaOJsonUmaVezSoEDevolveFalseNoSegundoClique() {
        when(spec.update()).thenReturn(1, 0);

        assertThat(repo.iniciarMapeamento(ID, 7L, "{\"documento\":\"CPF\"}")).isTrue();
        assertThat(repo.iniciarMapeamento(ID, 7L, "{}")).isFalse();

        verify(spec, times(2)).param("para", "UPLOADED");
        verify(spec, times(2)).param("de", "AWAITING_MAPPING");
        verify(spec).param("mapeamento", "{\"documento\":\"CPF\"}");
    }

    @Test
    void ultimoMapeamentoSoOlhaOsBatchesDoProprioTenant() {
        MappedQuerySpec<String> mapped = mappedMock();
        when(spec.query(String.class)).thenReturn(mapped);
        when(mapped.optional()).thenReturn(Optional.of("{\"documento\":\"CPF\"}"));

        assertThat(repo.ultimoMapeamento(7L, "CUSTOMER")).contains("{\"documento\":\"CPF\"}");

        assertThat(sqlExecutado()).contains("tenant_id = :tenant").contains("field_mapping IS NOT NULL");
        verify(spec).param("tenant", 7L);
        verify(spec).param("entidade", "CUSTOMER");
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

    // ───────────────────────── Trilha 3 e SLA ─────────────────────────

    @Test
    void inserirConciergeEntraNaFilaNaTrilha3ComPrazoEmHoras() {
        repo.inserirConcierge(ID, 7L, ID, "CUSTOMER", "1.0", null, null, OffsetDateTime.now().plusDays(30), 48);

        assertThat(sqlExecutado()).contains("concierge_deadline_at").contains("interval '1 hour'");
        verify(spec).param("status", "AWAITING_CONCIERGE");
        verify(spec).param("trilha", "CONCIERGE");
        verify(spec).param("horas", 48);
        verify(spec).param("tenant", 7L);
        verify(spec).update();
    }

    @Test
    void iniciarPreviaSoPegaPedidoDaFilaDaTrilha3UmaVezSo() {
        when(spec.update()).thenReturn(1, 0);

        assertThat(repo.iniciarPrevia(ID, 7L)).isTrue();
        assertThat(repo.iniciarPrevia(ID, 7L)).isFalse();

        verify(spec, times(2)).param("para", "UPLOADED");
        verify(spec, times(2)).param("de", "AWAITING_CONCIERGE");
        verify(spec, times(2)).param("trilha", "CONCIERGE");
    }

    @Test
    void reabrirFilaSoDesfazOQueEstaEmUploadedOuFailedDaTrilha3() {
        repo.reabrirFila(ID, 7L);

        assertThat(sqlExecutado()).contains("source_track = :trilha").contains("status IN (:uploaded, :falhou)");
        verify(spec).param("para", "AWAITING_CONCIERGE");
        verify(spec).param("uploaded", "UPLOADED");
        verify(spec).param("falhou", "FAILED");
    }

    @Test
    void recusarPreviaContaARecusaERenovaOPrazoSoEmPreviaDaTrilha3() {
        when(spec.update()).thenReturn(1, 0);

        assertThat(repo.recusarPrevia(ID, 7L, 48)).isTrue();
        assertThat(repo.recusarPrevia(ID, 7L, 48)).isFalse();      // já voltou à fila: segundo clique não conta outra recusa

        verify(spec, times(2)).param("ready", "READY");
        verify(spec, times(2)).param("fix", "AWAITING_FIX");
        verify(spec, times(2)).param("horas", 48);
        verify(jdbc, times(2)).sql(ArgumentMatchers.contains("concierge_rejections = concierge_rejections + 1"));
    }

    @Test
    void filaDoConciergeIgnoraFinalizadosEFalhosEOrdenaPeloPrazo() {
        MappedQuerySpec<ImportBatch> mapped = mappedMock();
        when(spec.query(ArgumentMatchers.<RowMapper<ImportBatch>>any())).thenReturn(mapped);
        when(mapped.list()).thenReturn(List.of());
        MappedQuerySpec<Long> contagem = mappedMock();
        when(spec.query(ArgumentMatchers.eq(Long.class))).thenReturn(contagem);
        when(contagem.single()).thenReturn(4L);

        assertThat(repo.listarFila(20, 0)).isEmpty();
        assertThat(repo.contarFila()).isEqualTo(4L);
        assertThat(repo.listarVencidos()).isEmpty();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(3)).sql(sql.capture());
        assertThat(sql.getAllValues().get(0)).contains("done_at IS NULL").contains("ORDER BY concierge_deadline_at");
        assertThat(sql.getAllValues().get(2)).contains("concierge_deadline_at < now()");
        verify(spec, times(3)).param("trilha", "CONCIERGE");
        verify(spec, times(3)).param("falhou", "FAILED");
    }

    @Test
    void escaladosSaoOsQueAtingiramOTetoDeRecusas() {
        MappedQuerySpec<Long> contagem = mappedMock();
        when(spec.query(ArgumentMatchers.eq(Long.class))).thenReturn(contagem);
        when(contagem.single()).thenReturn(2L);

        assertThat(repo.contarEscalados(3)).isEqualTo(2L);

        assertThat(sqlExecutado()).contains("concierge_rejections >= :max");
        verify(spec).param("max", 3);
    }

    @Test
    void onboardingsAgregaPorTenantSoAsEntidadesObrigatoriasEMapeiaAsConcluidas() throws SQLException {
        MappedQuerySpec<ImportBatchRepository.OnboardingLinha> mapped = mappedMock();
        when(spec.query(ArgumentMatchers.<RowMapper<ImportBatchRepository.OnboardingLinha>>any())).thenReturn(mapped);
        when(mapped.list()).thenReturn(List.of());

        assertThat(repo.onboardings(List.of("CUSTOMER", "PRODUCT"), 200)).isEmpty();

        assertThat(sqlExecutado()).contains("GROUP BY tenant_id").contains("entity_type IN (:entidades)");
        verify(spec).param("entidades", List.of("CUSTOMER", "PRODUCT"));
        verify(spec).param("limite", 200);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RowMapper<ImportBatchRepository.OnboardingLinha>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(spec).query(mapper.capture());
        ResultSet rs = Mockito.mock(ResultSet.class);
        OffsetDateTime inicio = OffsetDateTime.now().minusDays(3);
        when(rs.getLong("tenant_id")).thenReturn(7L);
        when(rs.getObject("iniciado_em", OffsetDateTime.class)).thenReturn(inicio);
        when(rs.getObject("ultimo_done", OffsetDateTime.class)).thenReturn(null);
        when(rs.getString("concluidas")).thenReturn("CUSTOMER,PRODUCT", "CUSTOMER,PRODUCT", "");

        assertThat(mapper.getValue().mapRow(rs, 0)).isEqualTo(new ImportBatchRepository.OnboardingLinha(7L, inicio, null, List.of("CUSTOMER", "PRODUCT")));
        assertThat(mapper.getValue().mapRow(rs, 1).concluidas()).isEmpty();
    }

    @Test
    void definirTotalFiltraPorTenant() {
        repo.definirTotal(ID, 7L, 2);

        assertThat(sqlExecutado()).contains("row_count = :linhas").contains("tenant_id = :tenant");
        verify(spec).param("linhas", 2);
        verify(spec).update();
    }
}
