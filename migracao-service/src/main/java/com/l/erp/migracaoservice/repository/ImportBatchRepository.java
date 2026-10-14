package com.l.erp.migracaoservice.repository;

import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.domain.ImportBatch;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acesso a migracao.import_batch via JdbcClient. Toda leitura e escrita recebe o tenant (spec migracao §14). */
@Repository
@RequiredArgsConstructor
public class ImportBatchRepository {

    private static final int FALHA_MAX = 300;
    private static final String COLUNAS = """
            id, tenant_id, requested_by, entity_type, schema_version, status, source_track, original_file_name,
            raw_file_path, row_count, failure_reason, created_at, updated_at, concierge_deadline_at, concierge_rejections""";

    private final JdbcClient jdbc;

    public void inserir(UUID id, Long tenantId, UUID requestedBy, String entidade, String versao, String trilha,
                        String nomeArquivo, String caminhoRelativo, OffsetDateTime purgarApos) {
        jdbc.sql("""
                        INSERT INTO migracao.import_batch (id, tenant_id, requested_by, entity_type, schema_version,
                                                           source_track, original_file_name, raw_file_path, purge_after)
                        VALUES (:id, :tenant, :user, :entidade, :versao, :trilha, :nome, :caminho, :purgar)""")
                .param("id", id).param("tenant", tenantId).param("user", requestedBy)
                .param("entidade", entidade).param("versao", versao).param("trilha", trilha)
                .param("nome", nomeArquivo).param("caminho", caminhoRelativo).param("purgar", purgarApos)
                .update();
    }

    public Optional<ImportBatch> buscar(UUID id, Long tenantId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM migracao.import_batch WHERE id = :id AND tenant_id = :tenant")
                .param("id", id).param("tenant", tenantId)
                .query(ImportBatchRepository::mapear).optional();
    }

    public List<ImportBatch> listar(Long tenantId, int limite, int deslocamento) {
        return jdbc.sql("SELECT " + COLUNAS + """
                         FROM migracao.import_batch WHERE tenant_id = :tenant
                        ORDER BY created_at DESC LIMIT :limite OFFSET :deslocamento""")
                .param("tenant", tenantId).param("limite", limite).param("deslocamento", deslocamento)
                .query(ImportBatchRepository::mapear).list();
    }

    public long contar(Long tenantId) {
        return jdbc.sql("SELECT count(*) FROM migracao.import_batch WHERE tenant_id = :tenant")
                .param("tenant", tenantId).query(Long.class).single();
    }

    public void definirTotal(UUID id, Long tenantId, int linhas) {
        jdbc.sql("UPDATE migracao.import_batch SET row_count = :linhas, updated_at = now() WHERE id = :id AND tenant_id = :tenant")
                .param("id", id).param("tenant", tenantId).param("linhas", linhas).update();
    }

    /** Trilha 2 (spec §7): as colunas do arquivo não batem com o modelo; o batch espera o cliente mapeá-las. */
    public void aguardarMapeamento(UUID id, Long tenantId) {
        jdbc.sql("""
                        UPDATE migracao.import_batch SET status = :para, source_track = :trilha, updated_at = now()
                         WHERE id = :id AND tenant_id = :tenant AND status = :de""")
                .param("id", id).param("tenant", tenantId).param("para", Constants.IMPORT_STATUS_AWAITING_MAPPING)
                .param("trilha", Constants.IMPORT_TRILHA_SELF_MAPPED).param("de", Constants.IMPORT_STATUS_UPLOADED).update();
    }

    /** AWAITING_MAPPING → UPLOADED gravando o mapeamento, uma vez só: false se já foi aplicado (duplo clique, duas abas). */
    public boolean iniciarMapeamento(UUID id, Long tenantId, String mapeamentoJson) {
        return jdbc.sql("""
                        UPDATE migracao.import_batch
                           SET status = :para, field_mapping = CAST(:mapeamento AS jsonb), updated_at = now()
                         WHERE id = :id AND tenant_id = :tenant AND status = :de""")
                .param("id", id).param("tenant", tenantId).param("para", Constants.IMPORT_STATUS_UPLOADED)
                .param("mapeamento", mapeamentoJson).param("de", Constants.IMPORT_STATUS_AWAITING_MAPPING).update() == 1;
    }

    /** Último mapeamento gravado por ESTE tenant para a entidade (spec §14: nunca sugerido a outro tenant). */
    public Optional<String> ultimoMapeamento(Long tenantId, String entidade) {
        return jdbc.sql("""
                        SELECT field_mapping::text FROM migracao.import_batch
                         WHERE tenant_id = :tenant AND entity_type = :entidade AND field_mapping IS NOT NULL
                         ORDER BY created_at DESC LIMIT 1""")
                .param("tenant", tenantId).param("entidade", entidade).query(String.class).optional();
    }

    /** UPLOADED/AWAITING_FIX → VALIDATING, uma vez só: false se outra thread já pegou o batch. */
    public boolean iniciarValidacao(UUID id, Long tenantId) {
        return jdbc.sql("""
                        UPDATE migracao.import_batch SET status = :para, updated_at = now(), failure_reason = NULL
                         WHERE id = :id AND tenant_id = :tenant AND status IN (:uploaded, :awaiting)""")
                .param("id", id).param("tenant", tenantId).param("para", Constants.IMPORT_STATUS_VALIDATING)
                .param("uploaded", Constants.IMPORT_STATUS_UPLOADED).param("awaiting", Constants.IMPORT_STATUS_AWAITING_FIX)
                .update() == 1;
    }

    /** VALIDATING → READY ou AWAITING_FIX; só age em VALIDATING, então não pisa num batch já resolvido. */
    public void concluirValidacao(UUID id, Long tenantId, String statusFinal) {
        jdbc.sql("""
                        UPDATE migracao.import_batch SET status = :para, updated_at = now()
                         WHERE id = :id AND tenant_id = :tenant AND status = :de""")
                .param("id", id).param("tenant", tenantId).param("para", statusFinal)
                .param("de", Constants.IMPORT_STATUS_VALIDATING).update();
    }

    /** READY → PROMOTING, uma vez só: false se o batch não está pronto ou outra requisição já o pegou. */
    public boolean iniciarPromocao(UUID id, Long tenantId) {
        return jdbc.sql("""
                        UPDATE migracao.import_batch SET status = :para, promoted_at = now(), updated_at = now()
                         WHERE id = :id AND tenant_id = :tenant AND status = :de""")
                .param("id", id).param("tenant", tenantId).param("para", Constants.IMPORT_STATUS_PROMOTING)
                .param("de", Constants.IMPORT_STATUS_READY).update() == 1;
    }

    /**
     * PROMOTING → DONE, na MESMA transação que grava o destino: se o timeout já marcou FAILED, devolve false e o
     * chamador desfaz tudo. A retenção do staging passa a contar daqui (spec §12: 30 dias após done_at).
     */
    public boolean concluirPromocao(UUID id, Long tenantId, int diasRetencao) {
        return jdbc.sql("""
                        UPDATE migracao.import_batch
                           SET status = :para, done_at = now(), updated_at = now(),
                               purge_after = now() + (:dias * interval '1 day')
                         WHERE id = :id AND tenant_id = :tenant AND status = :de""")
                .param("id", id).param("tenant", tenantId).param("para", Constants.IMPORT_STATUS_DONE)
                .param("de", Constants.IMPORT_STATUS_PROMOTING).param("dias", diasRetencao).update() == 1;
    }

    /** Spec §12.1: batch parado no estado assíncrono além do prazo vira FAILED (nunca retry automático). */
    public int expirarTravados(String status, int minutos, String motivo) {
        return jdbc.sql("""
                        UPDATE migracao.import_batch SET status = :falhou, failure_reason = :motivo, updated_at = now()
                         WHERE status = :status AND updated_at < now() - (:minutos * interval '1 minute')""")
                .param("falhou", Constants.IMPORT_STATUS_FAILED).param("motivo", motivo)
                .param("status", status).param("minutos", minutos).update();
    }

    public record Purgavel(UUID id, Long tenantId) {}

    /** Batches com retenção vencida e dados ainda presentes; os que estão em processamento esperam o timeout. */
    public List<Purgavel> listarParaPurgar(int limite) {
        return jdbc.sql("""
                        SELECT id, tenant_id FROM migracao.import_batch
                         WHERE purge_after < now() AND purged_at IS NULL AND status NOT IN (:validando, :promovendo)
                         ORDER BY purge_after LIMIT :limite""")
                .param("validando", Constants.IMPORT_STATUS_VALIDATING).param("promovendo", Constants.IMPORT_STATUS_PROMOTING)
                .param("limite", limite)
                .query((rs, n) -> new Purgavel(rs.getObject("id", UUID.class), rs.getLong("tenant_id"))).list();
    }

    /**
     * DONE e FAILED mantêm o estado e o motivo; um batch descartado em qualquer outro estado (pronto ou com erros, mas
     * nunca confirmado) vira FAILED, senão ficaria READY sem linhas e a confirmação gravaria zero registros.
     */
    public void marcarPurgado(UUID id, Long tenantId, String motivo) {
        jdbc.sql("""
                        UPDATE migracao.import_batch SET purged_at = now(), updated_at = now(),
                               status = CASE WHEN status IN (:done, :falhou) THEN status ELSE :falhou END,
                               failure_reason = CASE WHEN status IN (:done, :falhou) THEN failure_reason ELSE :motivo END
                         WHERE id = :id AND tenant_id = :tenant""")
                .param("id", id).param("tenant", tenantId).param("done", Constants.IMPORT_STATUS_DONE)
                .param("falhou", Constants.IMPORT_STATUS_FAILED).param("motivo", motivo).update();
    }

    /** Terminal (spec §5): nunca há retry automático; a mensagem diz ao cliente o que fazer. */
    public void falhar(UUID id, Long tenantId, String motivo) {
        String m = motivo != null && motivo.length() > FALHA_MAX ? motivo.substring(0, FALHA_MAX) : motivo;
        jdbc.sql("""
                        UPDATE migracao.import_batch SET status = :status, failure_reason = :motivo, updated_at = now()
                         WHERE id = :id AND tenant_id = :tenant""")
                .param("id", id).param("tenant", tenantId).param("status", Constants.IMPORT_STATUS_FAILED)
                .param("motivo", m).update();
    }

    private static ImportBatch mapear(ResultSet rs, int n) throws SQLException {
        return new ImportBatch(
                rs.getObject("id", UUID.class), rs.getLong("tenant_id"), rs.getObject("requested_by", UUID.class),
                rs.getString("entity_type"), rs.getString("schema_version"), rs.getString("status"),
                rs.getString("source_track"), rs.getString("original_file_name"), rs.getString("raw_file_path"),
                (Integer) rs.getObject("row_count"), rs.getString("failure_reason"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("concierge_deadline_at", OffsetDateTime.class), rs.getInt("concierge_rejections"));
    }

    // ───────────────────────── Trilha 3 — concierge (spec §8) ─────────────────────────

    /** Pedido de ajuda: entra na fila já com o prazo (agora + 48h). O arquivo é opcional (o cliente pode combinar o envio depois). */
    public void inserirConcierge(UUID id, Long tenantId, UUID requestedBy, String entidade, String versao,
                                 String nomeArquivo, String caminhoRelativo, OffsetDateTime purgarApos, int prazoHoras) {
        jdbc.sql("""
                        INSERT INTO migracao.import_batch (id, tenant_id, requested_by, entity_type, schema_version, status,
                                                           source_track, original_file_name, raw_file_path, purge_after,
                                                           concierge_deadline_at)
                        VALUES (:id, :tenant, :user, :entidade, :versao, :status, :trilha, :nome, :caminho, :purgar,
                                now() + (:horas * interval '1 hour'))""")
                .param("id", id).param("tenant", tenantId).param("user", requestedBy)
                .param("entidade", entidade).param("versao", versao).param("status", Constants.IMPORT_STATUS_AWAITING_CONCIERGE)
                .param("trilha", Constants.IMPORT_TRILHA_CONCIERGE).param("nome", nomeArquivo)
                .param("caminho", caminhoRelativo).param("purgar", purgarApos).param("horas", prazoHoras).update();
    }

    /** Fila → UPLOADED para o concierge carregar a prévia, uma vez só (duas cargas simultâneas: a segunda leva false). */
    public boolean iniciarPrevia(UUID id, Long tenantId) {
        return jdbc.sql("""
                        UPDATE migracao.import_batch SET status = :para, updated_at = now(), failure_reason = NULL
                         WHERE id = :id AND tenant_id = :tenant AND source_track = :trilha AND status = :de""")
                .param("id", id).param("tenant", tenantId).param("para", Constants.IMPORT_STATUS_UPLOADED)
                .param("trilha", Constants.IMPORT_TRILHA_CONCIERGE).param("de", Constants.IMPORT_STATUS_AWAITING_CONCIERGE)
                .update() == 1;
    }

    /** A carga da prévia falhou (arquivo do concierge fora do modelo): o pedido volta à fila, nunca vira FAILED do cliente. */
    public void reabrirFila(UUID id, Long tenantId) {
        jdbc.sql("""
                        UPDATE migracao.import_batch SET status = :para, updated_at = now(), failure_reason = NULL, row_count = NULL
                         WHERE id = :id AND tenant_id = :tenant AND source_track = :trilha AND status IN (:uploaded, :falhou)""")
                .param("id", id).param("tenant", tenantId).param("para", Constants.IMPORT_STATUS_AWAITING_CONCIERGE)
                .param("trilha", Constants.IMPORT_TRILHA_CONCIERGE).param("uploaded", Constants.IMPORT_STATUS_UPLOADED)
                .param("falhou", Constants.IMPORT_STATUS_FAILED).update();
    }

    /** Cliente recusa a prévia (READY/AWAITING_FIX): conta a recusa, renova o prazo e devolve o pedido à fila. */
    public boolean recusarPrevia(UUID id, Long tenantId, int prazoHoras) {
        return jdbc.sql("""
                        UPDATE migracao.import_batch
                           SET status = :para, concierge_rejections = concierge_rejections + 1, row_count = NULL,
                               concierge_deadline_at = now() + (:horas * interval '1 hour'), updated_at = now()
                         WHERE id = :id AND tenant_id = :tenant AND source_track = :trilha AND status IN (:ready, :fix)""")
                .param("id", id).param("tenant", tenantId).param("para", Constants.IMPORT_STATUS_AWAITING_CONCIERGE)
                .param("horas", prazoHoras).param("trilha", Constants.IMPORT_TRILHA_CONCIERGE)
                .param("ready", Constants.IMPORT_STATUS_READY).param("fix", Constants.IMPORT_STATUS_AWAITING_FIX)
                .update() == 1;
    }

    // ───────────────────────── Suporte (todos os tenants) e SLA (spec §13) ─────────────────────────

    public Optional<ImportBatch> buscarAdmin(UUID id) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM migracao.import_batch WHERE id = :id")
                .param("id", id).query(ImportBatchRepository::mapear).optional();
    }

    // String simples (não text block): o text block descarta o espaço inicial e o SQL colava em "import_batchWHERE"
    private static final String FILA_ABERTA = " WHERE source_track = :trilha AND done_at IS NULL AND status <> :falhou";

    /** Fila do concierge: pedidos abertos, o prazo mais próximo (ou já vencido) primeiro. */
    public List<ImportBatch> listarFila(int limite, int deslocamento) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM migracao.import_batch" + FILA_ABERTA
                        + " ORDER BY concierge_deadline_at NULLS LAST LIMIT :limite OFFSET :deslocamento")
                .param("trilha", Constants.IMPORT_TRILHA_CONCIERGE).param("falhou", Constants.IMPORT_STATUS_FAILED)
                .param("limite", limite).param("deslocamento", deslocamento)
                .query(ImportBatchRepository::mapear).list();
    }

    public long contarFila() {
        return jdbc.sql("SELECT count(*) FROM migracao.import_batch" + FILA_ABERTA)
                .param("trilha", Constants.IMPORT_TRILHA_CONCIERGE).param("falhou", Constants.IMPORT_STATUS_FAILED)
                .query(Long.class).single();
    }

    /** Pedidos abertos cujo prazo de 48h já passou (alimenta o alerta diário e o painel). */
    public List<ImportBatch> listarVencidos() {
        return jdbc.sql("SELECT " + COLUNAS + " FROM migracao.import_batch" + FILA_ABERTA
                        + " AND concierge_deadline_at < now() ORDER BY concierge_deadline_at")
                .param("trilha", Constants.IMPORT_TRILHA_CONCIERGE).param("falhou", Constants.IMPORT_STATUS_FAILED)
                .query(ImportBatchRepository::mapear).list();
    }

    public long contarEscalados(int recusasMax) {
        return jdbc.sql("SELECT count(*) FROM migracao.import_batch" + FILA_ABERTA + " AND concierge_rejections >= :max")
                .param("trilha", Constants.IMPORT_TRILHA_CONCIERGE).param("falhou", Constants.IMPORT_STATUS_FAILED)
                .param("max", recusasMax).query(Long.class).single();
    }

    /** Um onboarding derivado de import_batch: entidades obrigatórias já em DONE e quando começou (spec §13, sem tabela própria). */
    public record OnboardingLinha(Long tenantId, OffsetDateTime iniciadoEm, OffsetDateTime ultimoDone, List<String> concluidas) {}

    public List<OnboardingLinha> onboardings(Collection<String> entidadesObrigatorias, int limite) {
        return jdbc.sql("""
                        SELECT tenant_id, min(created_at) AS iniciado_em,
                               max(done_at) FILTER (WHERE status = :done) AS ultimo_done,
                               coalesce(string_agg(DISTINCT entity_type, ',') FILTER (WHERE status = :done), '') AS concluidas
                          FROM migracao.import_batch WHERE entity_type IN (:entidades)
                         GROUP BY tenant_id ORDER BY min(created_at) LIMIT :limite""")
                .param("done", Constants.IMPORT_STATUS_DONE).param("entidades", entidadesObrigatorias).param("limite", limite)
                .query((rs, n) -> new OnboardingLinha(rs.getLong("tenant_id"),
                        rs.getObject("iniciado_em", OffsetDateTime.class), rs.getObject("ultimo_done", OffsetDateTime.class),
                        rs.getString("concluidas").isEmpty() ? List.of() : List.of(rs.getString("concluidas").split(","))))
                .list();
    }
}
