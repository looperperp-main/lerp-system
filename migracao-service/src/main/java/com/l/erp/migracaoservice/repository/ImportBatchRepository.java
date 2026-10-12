package com.l.erp.migracaoservice.repository;

import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.domain.ImportBatch;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
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
            raw_file_path, row_count, failure_reason, created_at, updated_at""";

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
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }
}
