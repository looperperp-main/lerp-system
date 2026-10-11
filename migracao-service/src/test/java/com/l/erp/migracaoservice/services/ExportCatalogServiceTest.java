package com.l.erp.migracaoservice.services;

import com.l.erp.migracaoservice.domain.CatalogTable;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

class ExportCatalogServiceTest {

    private static Map<String, Object> col(String schema, String tabela, String coluna) {
        return Map.of("table_schema", schema, "table_name", tabela, "column_name", coluna);
    }

    private static ExportCatalogService comBanco(List<Map<String, Object>> colunas, List<Map<String, Object>> fks) {
        JdbcClient jdbc = Mockito.mock(JdbcClient.class, RETURNS_DEEP_STUBS);
        when(jdbc.sql(contains("information_schema.columns")).param(eq("schemas"), any()).query().listOfRows()).thenReturn(colunas);
        when(jdbc.sql(contains("FOREIGN KEY")).param(eq("schemas"), any()).query().listOfRows()).thenReturn(fks);
        return new ExportCatalogService(jdbc);
    }

    @Test
    void tabelaComTenantIdEntraComFiltroDiretoEColunaDeData() {
        var svc = comBanco(List.of(
                col("cadastros", "cliente", "id"), col("cadastros", "cliente", "tenant_id"), col("cadastros", "cliente", "created_at")),
                List.of());

        List<CatalogTable> r = svc.descobrir();

        assertThat(r).singleElement().satisfies(t -> {
            assertThat(t.key()).isEqualTo("cadastros.cliente");
            assertThat(t.tenantFilter()).isEqualTo("\"tenant_id\" = ?");
            assertThat(t.dateColumn()).isEqualTo("created_at");
            assertThat(t.label()).isEqualTo("Cliente");
        });
    }

    @Test
    void tabelaFilhaSemTenantIdIsolaPelaFkDoPai() {
        var svc = comBanco(List.of(
                col("cadastros", "condicao_pagamento", "id"), col("cadastros", "condicao_pagamento", "tenant_id"),
                col("cadastros", "condicao_pagamento_parcela", "id"), col("cadastros", "condicao_pagamento_parcela", "condicao_pagamento_id")),
                List.of(Map.of("table_schema", "cadastros", "table_name", "condicao_pagamento_parcela",
                        "column_name", "condicao_pagamento_id", "ref_schema", "cadastros",
                        "ref_table", "condicao_pagamento", "ref_column", "id")));

        CatalogTable filha = svc.descobrir().stream()
                .filter(t -> t.table().equals("condicao_pagamento_parcela")).findFirst().orElseThrow();

        assertThat(filha.tenantFilter()).isEqualTo(
                "\"condicao_pagamento_id\" IN (SELECT \"id\" FROM \"cadastros\".\"condicao_pagamento\" WHERE \"tenant_id\" = ?)");
        assertThat(filha.dateColumn()).isNull();
    }

    @Test
    void tabelaSemTenantEsemFkResolvivelNuncaEntra() {
        var svc = comBanco(List.of(col("emissao", "uf_autorizador", "id"), col("emissao", "uf_autorizador", "uf")), List.of());

        assertThat(svc.descobrir()).isEmpty();
    }

    @Test
    void tabelasExcluidasNuncaEntramMesmoComTenantId() {
        var svc = comBanco(List.of(
                col("emissao", "certificado_digital", "tenant_id"), col("emissao", "certificado_digital", "pfx"),
                col("emissao", "configuracao_nfse", "tenant_id"),
                col("emissao", "documento_fiscal", "tenant_id"), col("emissao", "documento_fiscal", "created_at")),
                List.of());

        assertThat(svc.descobrir()).extracting(CatalogTable::key).containsExactly("emissao.documento_fiscal");
    }

    @Test
    void filhaCujoPaiEhExcluidoNaoEntra() {
        var svc = comBanco(List.of(
                col("emissao", "certificado_digital", "id"), col("emissao", "certificado_digital", "tenant_id"),
                col("emissao", "certificado_item", "cert_id")),
                List.of(Map.of("table_schema", "emissao", "table_name", "certificado_item", "column_name", "cert_id",
                        "ref_schema", "emissao", "ref_table", "certificado_digital", "ref_column", "id")));

        assertThat(svc.descobrir()).isEmpty();
    }

    @Test
    void identificadoresSaoCitadosELimpos() {
        assertThat(ExportCatalogService.aspas("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(ExportCatalogService.humanizar("pedido_compra_item")).isEqualTo("Pedido compra item");
        assertThat(ExportCatalogService.rotuloGrupo("emissao")).isEqualTo("Emissão Fiscal");
        assertThat(ExportCatalogService.rotuloGrupo("compras")).isEqualTo("Compras");
    }
}
