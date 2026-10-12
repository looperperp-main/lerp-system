package com.l.erp.migracaoservice.repository;

import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.domain.ExportBatch;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a migracao.export_batch / export_audit_log via JdbcClient (sem JPA: as colunas JSONB e
 * a extração em streaming já são JDBC). Toda leitura recebe o tenant — nunca há busca só por id.
 */
@Repository
@RequiredArgsConstructor
public class ExportBatchRepository {

    private static final String COLUNAS = """
            id, tenant_id, requested_by, scope::text AS scope, format, status, file_paths::text AS file_paths,
            table_counts::text AS table_counts, attempt_count, reason, failure_reason,
            created_at, ready_at, expires_at, downloaded_at""";

    private final JdbcClient jdbc;

    public void inserir(UUID id, Long tenantId, UUID requestedBy, String scopeJson, String scopeHash,
                        String formato, String motivo) {
        jdbc.sql("""
                        INSERT INTO migracao.export_batch (id, tenant_id, requested_by, scope, scope_hash, format, reason)
                        VALUES (:id, :tenant, :user, CAST(:scope AS jsonb), :hash, :formato, :motivo)""")
                .param("id", id).param("tenant", tenantId).param("user", requestedBy)
                .param("scope", scopeJson).param("hash", scopeHash)
                .param("formato", formato).param("motivo", motivo)
                .update();
    }

    public Optional<ExportBatch> buscar(UUID id, Long tenantId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM migracao.export_batch WHERE id = :id AND tenant_id = :tenant")
                .param("id", id).param("tenant", tenantId)
                .query(ExportBatchRepository::mapear).optional();
    }

    /** Única busca sem tenant: o token É a credencial; quem chama compara o tenant e audita a negativa. */
    public Optional<ExportBatch> buscarPorToken(String token) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM migracao.export_batch WHERE download_token = :token")
                .param("token", token).query(ExportBatchRepository::mapear).optional();
    }

    /** O token fica fora do ExportBatch de propósito (não circula em logs nem em respostas); só o e-mail de posse o lê. */
    public Optional<String> tokenDe(UUID id) {
        return jdbc.sql("SELECT download_token FROM migracao.export_batch WHERE id = :id AND download_token IS NOT NULL")
                .param("id", id).query(String.class).optional();
    }

    /** READY ainda não baixado, vencendo em até {@code horas}, sem aviso enviado: candidatos ao e-mail de 24h antes. */
    public List<ExportBatch> listarParaAvisoDeExpiracao(int horas, int limite) {
        return jdbc.sql("SELECT " + COLUNAS + """
                         FROM migracao.export_batch
                        WHERE status = :status AND expiry_notice_sent_at IS NULL
                          AND expires_at > now() AND expires_at <= now() + make_interval(hours => :horas)
                        ORDER BY expires_at LIMIT :limite""")
                .param("status", Constants.EXPORT_STATUS_READY).param("horas", horas).param("limite", limite)
                .query(ExportBatchRepository::mapear).list();
    }

    /** Reserva o aviso: só uma execução recebe true (a outra instância, ou o próximo ciclo, vê já enviado). */
    public boolean marcarAvisoEnviado(UUID id) {
        return jdbc.sql("UPDATE migracao.export_batch SET expiry_notice_sent_at = now() WHERE id = :id AND expiry_notice_sent_at IS NULL")
                .param("id", id).update() == 1;
    }

    /** READY → DOWNLOADED; baixar de novo dentro da validade mantém DOWNLOADED e a data do primeiro download. */
    public void marcarBaixado(UUID id) {
        jdbc.sql("""
                        UPDATE migracao.export_batch
                           SET status = :para, downloaded_at = COALESCE(downloaded_at, now())
                         WHERE id = :id AND status IN (:ready, :para)""")
                .param("id", id).param("para", Constants.EXPORT_STATUS_DOWNLOADED).param("ready", Constants.EXPORT_STATUS_READY)
                .update();
    }

    /** Suporte (admin): sem filtro de tenant de propósito; só o AdminExportController, restrito a APP_OWNER, chama. */
    public Optional<ExportBatch> buscarDeQualquerTenant(UUID id) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM migracao.export_batch WHERE id = :id")
                .param("id", id).query(ExportBatchRepository::mapear).optional();
    }

    /** Suporte (admin): tenant e status opcionais (null = todos). O WHERE é montado só com o que veio. */
    public List<ExportBatch> listarAdmin(Long tenantId, String status, int limite, int deslocamento) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUNAS + " FROM migracao.export_batch");
        sql.append(filtroAdmin(tenantId, status))
                .append(" ORDER BY created_at DESC LIMIT :limite OFFSET :deslocamento");
        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString()).param("limite", limite).param("deslocamento", deslocamento);
        return parametrosAdmin(spec, tenantId, status).query(ExportBatchRepository::mapear).list();
    }

    public long contarAdmin(Long tenantId, String status) {
        JdbcClient.StatementSpec spec = jdbc.sql("SELECT count(*) FROM migracao.export_batch" + filtroAdmin(tenantId, status));
        return parametrosAdmin(spec, tenantId, status).query(Long.class).single();
    }

    private static String filtroAdmin(Long tenantId, String status) {
        String tenant = tenantId != null ? "tenant_id = :tenant" : null;
        String estado = status != null ? "status = :status" : null;
        if (tenant == null && estado == null) return "";
        return " WHERE " + (tenant != null && estado != null ? tenant + " AND " + estado : tenant != null ? tenant : estado);
    }

    private static JdbcClient.StatementSpec parametrosAdmin(JdbcClient.StatementSpec spec, Long tenantId, String status) {
        if (tenantId != null) spec = spec.param("tenant", tenantId);
        if (status != null) spec = spec.param("status", status);
        return spec;
    }

    /** Dedupe (spec §5.2): batch READY, mesmo escopo, ainda dentro da validade. */
    public Optional<ExportBatch> buscarProntoPorEscopo(Long tenantId, String scopeHash) {
        return jdbc.sql("SELECT " + COLUNAS + """
                         FROM migracao.export_batch
                        WHERE tenant_id = :tenant AND scope_hash = :hash AND status = :status AND expires_at > now()
                        ORDER BY created_at DESC LIMIT 1""")
                .param("tenant", tenantId).param("hash", scopeHash).param("status", Constants.EXPORT_STATUS_READY)
                .query(ExportBatchRepository::mapear).optional();
    }

    public List<ExportBatch> listar(Long tenantId, int limite, int deslocamento) {
        return jdbc.sql("SELECT " + COLUNAS + """
                         FROM migracao.export_batch WHERE tenant_id = :tenant
                        ORDER BY created_at DESC LIMIT :limite OFFSET :deslocamento""")
                .param("tenant", tenantId).param("limite", limite).param("deslocamento", deslocamento)
                .query(ExportBatchRepository::mapear).list();
    }

    public long contar(Long tenantId) {
        return jdbc.sql("SELECT count(*) FROM migracao.export_batch WHERE tenant_id = :tenant")
                .param("tenant", tenantId).query(Long.class).single();
    }

    /** REQUESTED → PROCESSING, uma vez só: devolve false se outra thread já pegou o batch. */
    public boolean marcarProcessando(UUID id) {
        return jdbc.sql("""
                        UPDATE migracao.export_batch
                           SET status = :para, attempt_count = attempt_count + 1, started_at = now(), failure_reason = NULL
                         WHERE id = :id AND status = :de""")
                .param("id", id).param("de", Constants.EXPORT_STATUS_REQUESTED).param("para", Constants.EXPORT_STATUS_PROCESSING)
                .update() == 1;
    }

    /** Pode lançar DuplicateKeyException se o token colidir (o chamador gera outro e repete). */
    public void marcarPronto(UUID id, String filePathsJson, String tableCountsJson, String token, OffsetDateTime expiraEm) {
        jdbc.sql("""
                        UPDATE migracao.export_batch
                           SET status = :status, file_paths = CAST(:paths AS jsonb), table_counts = CAST(:counts AS jsonb),
                               download_token = :token, ready_at = now(), expires_at = :expira
                         WHERE id = :id""")
                .param("id", id).param("status", Constants.EXPORT_STATUS_READY)
                .param("paths", filePathsJson).param("counts", tableCountsJson)
                .param("token", token).param("expira", expiraEm)
                .update();
    }

    public void marcarFalhou(UUID id, String motivo) {
        jdbc.sql("UPDATE migracao.export_batch SET status = :status, failure_reason = :motivo WHERE id = :id")
                .param("id", id).param("status", Constants.EXPORT_STATUS_FAILED).param("motivo", motivo).update();
    }

    /**
     * Falha inesperada: volta a REQUESTED se ainda há tentativa (started_at vira a marca de espera do retry),
     * senão FAILED. Só age em PROCESSING, então não pisa num batch que a manutenção já resolveu.
     */
    public boolean reenfileirarOuFalhar(UUID id, int maxTentativas, String motivo) {
        return jdbc.sql("""
                        UPDATE migracao.export_batch
                           SET status = CASE WHEN attempt_count < :max THEN :requested ELSE :failed END,
                               started_at = now(), failure_reason = :motivo
                         WHERE id = :id AND status = :processing""")
                .param("id", id).param("max", maxTentativas).param("motivo", motivo)
                .param("requested", Constants.EXPORT_STATUS_REQUESTED).param("failed", Constants.EXPORT_STATUS_FAILED)
                .param("processing", Constants.EXPORT_STATUS_PROCESSING)
                .update() == 1;
    }

    /** Prontos/baixados cuja validade passou (a faxina às 03:00 apaga os arquivos e marca EXPIRED). */
    public List<ExportBatch> listarExpirados(int limite) {
        return jdbc.sql("SELECT " + COLUNAS + """
                         FROM migracao.export_batch
                        WHERE status IN (:ready, :downloaded) AND expires_at <= now()
                        ORDER BY expires_at LIMIT :limite""")
                .param("ready", Constants.EXPORT_STATUS_READY).param("downloaded", Constants.EXPORT_STATUS_DOWNLOADED)
                .param("limite", limite).query(ExportBatchRepository::mapear).list();
    }

    public void marcarExpirado(UUID id) {
        jdbc.sql("UPDATE migracao.export_batch SET status = :status WHERE id = :id AND status IN (:ready, :downloaded)")
                .param("id", id).param("status", Constants.EXPORT_STATUS_EXPIRED)
                .param("ready", Constants.EXPORT_STATUS_READY).param("downloaded", Constants.EXPORT_STATUS_DOWNLOADED)
                .update();
    }

    /** PROCESSING há mais de {@code minutos}: a thread morreu (reinício) ou travou. */
    public List<ExportBatch> listarTravados(int minutos, int limite) {
        return jdbc.sql("SELECT " + COLUNAS + """
                         FROM migracao.export_batch
                        WHERE status = :status AND started_at < now() - make_interval(mins => :minutos)
                        ORDER BY started_at LIMIT :limite""")
                .param("status", Constants.EXPORT_STATUS_PROCESSING).param("minutos", minutos).param("limite", limite)
                .query(ExportBatchRepository::mapear).list();
    }

    /** REQUESTED parado há mais de {@code minutos} (job perdido ou retry em espera); started_at nulo cai no created_at. */
    public List<ExportBatch> listarPendentesAntigos(int minutos, int limite) {
        return jdbc.sql("SELECT " + COLUNAS + """
                         FROM migracao.export_batch
                        WHERE status = :status AND COALESCE(started_at, created_at) < now() - make_interval(mins => :minutos)
                        ORDER BY created_at LIMIT :limite""")
                .param("status", Constants.EXPORT_STATUS_REQUESTED).param("minutos", minutos).param("limite", limite)
                .query(ExportBatchRepository::mapear).list();
    }

    public void registrarAuditoria(UUID batchId, Long tenantId, String acao, UUID ator) {
        registrarAuditoria(batchId, tenantId, acao, ator, null);
    }

    /** {@code ip} só vem no download de posse (sem sessão, spec §10.1): é a única prova de quem baixou. */
    public void registrarAuditoria(UUID batchId, Long tenantId, String acao, UUID ator, String ip) {
        jdbc.sql("INSERT INTO migracao.export_audit_log (export_batch_id, tenant_id, action, actor_id, ip_address) VALUES (:batch, :tenant, :acao, :ator, :ip)")
                .param("batch", batchId).param("tenant", tenantId).param("acao", acao).param("ator", ator).param("ip", ip)
                .update();
    }

    public List<AuditoriaLinha> listarAuditoria(UUID batchId, Long tenantId) {
        return jdbc.sql("""
                        SELECT action, actor_id, created_at FROM migracao.export_audit_log
                         WHERE export_batch_id = :batch AND tenant_id = :tenant ORDER BY created_at, id""")
                .param("batch", batchId).param("tenant", tenantId)
                .query((rs, n) -> new AuditoriaLinha(rs.getString("action"), rs.getObject("actor_id", UUID.class),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .list();
    }

    public record AuditoriaLinha(String acao, UUID ator, OffsetDateTime em) {}

    private static ExportBatch mapear(ResultSet rs, int n) throws SQLException {
        return new ExportBatch(
                rs.getObject("id", UUID.class), rs.getLong("tenant_id"), rs.getObject("requested_by", UUID.class),
                rs.getString("scope"), rs.getString("format"), rs.getString("status"),
                rs.getString("file_paths"), rs.getString("table_counts"), rs.getInt("attempt_count"),
                rs.getString("reason"), rs.getString("failure_reason"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("ready_at", OffsetDateTime.class),
                rs.getObject("expires_at", OffsetDateTime.class), rs.getObject("downloaded_at", OffsetDateTime.class));
    }
}
