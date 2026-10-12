package com.l.erp.migracaoservice.repository;

import com.l.erp.common.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Staging (migracao.raw_import_row). Inserção e gravação de resultado em lote via JdbcTemplate
 * (50.000 linhas por arquivo); leitura via JdbcClient. Toda query filtra por tenant e batch.
 */
@Repository
@RequiredArgsConstructor
public class ImportRowRepository {

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    public record LinhaNova(int numero, String rawJson) {}

    public record LinhaStaging(long id, int numero, String rawJson) {}

    public record Resultado(long id, String status, String errosJson) {}

    public record LinhaComErro(int numero, String rawJson, String errosJson) {}

    public void inserirLote(Long tenantId, UUID batchId, String entidade, String trilha, List<LinhaNova> linhas) {
        jdbcTemplate.batchUpdate("""
                        INSERT INTO migracao.raw_import_row (tenant_id, batch_id, entity_type, row_num, raw_json, source_track)
                        VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?)""",
                linhas, linhas.size(), (ps, l) -> {
                    ps.setLong(1, tenantId);
                    ps.setObject(2, batchId);
                    ps.setString(3, entidade);
                    ps.setInt(4, l.numero());
                    ps.setString(5, l.rawJson());
                    ps.setString(6, trilha);
                });
    }

    /** Paginação por chave (id &gt; último): não degrada com OFFSET em 50.000 linhas. */
    public List<LinhaStaging> proximas(UUID batchId, Long tenantId, long depoisDoId, int limite) {
        return jdbc.sql("""
                        SELECT id, row_num, raw_json::text AS raw_json FROM migracao.raw_import_row
                         WHERE batch_id = :batch AND tenant_id = :tenant AND id > :depois
                         ORDER BY id LIMIT :limite""")
                .param("batch", batchId).param("tenant", tenantId).param("depois", depoisDoId).param("limite", limite)
                .query((rs, n) -> new LinhaStaging(rs.getLong("id"), rs.getInt("row_num"), rs.getString("raw_json")))
                .list();
    }

    /** Só as linhas VALID: é o que a promoção grava no destino. */
    public List<LinhaStaging> proximasValidas(UUID batchId, Long tenantId, long depoisDoId, int limite) {
        return jdbc.sql("""
                        SELECT id, row_num, raw_json::text AS raw_json FROM migracao.raw_import_row
                         WHERE batch_id = :batch AND tenant_id = :tenant AND status = :status AND id > :depois
                         ORDER BY id LIMIT :limite""")
                .param("batch", batchId).param("tenant", tenantId).param("status", Constants.IMPORT_LINHA_VALID)
                .param("depois", depoisDoId).param("limite", limite)
                .query((rs, n) -> new LinhaStaging(rs.getLong("id"), rs.getInt("row_num"), rs.getString("raw_json")))
                .list();
    }

    /** Desfecho da promoção por linha: PROMOTED, ou SKIPPED com o motivo no error_detail. */
    public void gravarPromocao(Long tenantId, List<Resultado> resultados) {
        jdbcTemplate.batchUpdate("""
                        UPDATE migracao.raw_import_row
                           SET status = ?, error_detail = CAST(? AS jsonb), promoted_at = now()
                         WHERE id = ? AND tenant_id = ?""",
                resultados, resultados.size(), (ps, r) -> {
                    ps.setString(1, r.status());
                    ps.setString(2, r.errosJson());
                    ps.setLong(3, r.id());
                    ps.setLong(4, tenantId);
                });
    }

    /** Purga LGPD (spec §12): apaga as linhas do staging, que guardam dado pessoal de terceiros. */
    public int purgarLinhas(UUID batchId, Long tenantId) {
        return jdbc.sql("DELETE FROM migracao.raw_import_row WHERE batch_id = :batch AND tenant_id = :tenant")
                .param("batch", batchId).param("tenant", tenantId).update();
    }

    public void gravarResultados(Long tenantId, List<Resultado> resultados) {
        jdbcTemplate.batchUpdate("""
                        UPDATE migracao.raw_import_row
                           SET status = ?, error_detail = CAST(? AS jsonb), validated_at = now()
                         WHERE id = ? AND tenant_id = ?""",
                resultados, resultados.size(), (ps, r) -> {
                    ps.setString(1, r.status());
                    ps.setString(2, r.errosJson());
                    ps.setLong(3, r.id());
                    ps.setLong(4, tenantId);
                });
    }

    /** batch → (status → quantidade), numa query só para a página inteira de batches. */
    public Map<UUID, Map<String, Long>> contarPorStatus(Long tenantId, Collection<UUID> batchIds) {
        Map<UUID, Map<String, Long>> out = new HashMap<>();
        if (batchIds.isEmpty()) return out;
        jdbc.sql("""
                        SELECT batch_id, status, count(*) AS total FROM migracao.raw_import_row
                         WHERE tenant_id = :tenant AND batch_id IN (:batches) GROUP BY batch_id, status""")
                .param("tenant", tenantId).param("batches", batchIds)
                .query((rs, n) -> {
                    out.computeIfAbsent(rs.getObject("batch_id", UUID.class), k -> new HashMap<>())
                            .put(rs.getString("status"), rs.getLong("total"));
                    return null;
                }).list();
        return out;
    }

    /** Linhas com erro em ordem de linha da planilha, para o relatório do cliente (spec §9). */
    public List<LinhaComErro> listarErros(UUID batchId, Long tenantId, int limite, int deslocamento) {
        return jdbc.sql("""
                        SELECT row_num, raw_json::text AS raw_json, error_detail::text AS error_detail
                          FROM migracao.raw_import_row
                         WHERE batch_id = :batch AND tenant_id = :tenant AND status = :status
                         ORDER BY row_num LIMIT :limite OFFSET :deslocamento""")
                .param("batch", batchId).param("tenant", tenantId).param("status", Constants.IMPORT_LINHA_ERROR)
                .param("limite", limite).param("deslocamento", deslocamento)
                .query((rs, n) -> new LinhaComErro(rs.getInt("row_num"), rs.getString("raw_json"), rs.getString("error_detail")))
                .list();
    }
}
