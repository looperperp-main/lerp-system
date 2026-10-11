package com.l.erp.migracaoservice.services;

import com.l.erp.migracaoservice.domain.CatalogTable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Só a montagem do SQL (pura). A extração em si precisa de Postgres e fica para teste de integração. */
class ExportExtractionServiceTest {

    private static final CatalogTable COM_DATA =
            new CatalogTable("vendas", "pedido", "Vendas", "Pedido", "\"tenant_id\" = ?", "created_at");
    private static final CatalogTable SEM_DATA =
            new CatalogTable("vendas", "pedido_sequencia", "Vendas", "Pedido sequencia", "\"tenant_id\" = ?", null);

    @Test
    void semPeriodoSoFiltraPorTenant() {
        assertThat(ExportExtractionService.montarSql(COM_DATA, false, false))
                .isEqualTo("SELECT * FROM \"vendas\".\"pedido\" WHERE \"tenant_id\" = ?");
    }

    @Test
    void periodoVirIntervaloFechadoNoInicioEAbertoNoFim() {
        assertThat(ExportExtractionService.montarSql(COM_DATA, true, true))
                .isEqualTo("SELECT * FROM \"vendas\".\"pedido\" WHERE \"tenant_id\" = ?"
                        + " AND \"created_at\" >= ?::date AND \"created_at\" < (?::date + 1)");
    }

    @Test
    void tabelaSemColunaDeDataIgnoraOPeriodo() {
        assertThat(ExportExtractionService.montarSql(SEM_DATA, true, true))
                .isEqualTo("SELECT * FROM \"vendas\".\"pedido_sequencia\" WHERE \"tenant_id\" = ?");
    }

    @Test
    void extensaoSegueOFormato() {
        assertThat(ExportExtractionService.extensao("CSV")).isEqualTo(".csv");
        assertThat(ExportExtractionService.extensao("XLSX")).isEqualTo(".xlsx");
    }
}
