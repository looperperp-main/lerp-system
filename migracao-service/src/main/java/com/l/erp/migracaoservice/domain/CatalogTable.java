package com.l.erp.migracaoservice.domain;

/**
 * Tabela exportável descoberta do information_schema.
 *
 * @param tenantFilter fragmento SQL com UM placeholder {@code ?} para o tenant (vem sempre do
 *                     catálogo, nunca do usuário)
 * @param dateColumn   coluna do filtro de período, ou null se a tabela sempre sai completa
 */
public record CatalogTable(String schema, String table, String groupLabel, String label,
                           String tenantFilter, String dateColumn) {

    /** Identificador estável usado pela API ("cadastros.cliente"). */
    public String key() {
        return schema + "." + table;
    }
}
