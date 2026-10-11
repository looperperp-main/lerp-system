package com.l.erp.migracaoservice.util;

import com.l.erp.migracaoservice.domain.ExportScope;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Hash do escopo, token de download e normalização de valores — as três peças puras do export. */
class UtilExportTest {

    private static final ExportScope ESCOPO = new ExportScope(List.of("cadastros.cliente", "vendas.pedido"), null, null);

    @Test
    void hashEhDeterministicoEComSha256Hex() {
        String h = ExportScopeHasher.hash(ESCOPO, "XLSX");

        assertThat(h).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(ExportScopeHasher.hash(ESCOPO, "XLSX")).isEqualTo(h);
    }

    @Test
    void hashMudaComFormatoPeriodoETabelas() {
        String base = ExportScopeHasher.hash(ESCOPO, "XLSX");

        assertThat(ExportScopeHasher.hash(ESCOPO, "CSV")).isNotEqualTo(base);
        assertThat(ExportScopeHasher.hash(new ExportScope(ESCOPO.tabelas(), LocalDate.of(2026, 1, 1), null), "XLSX")).isNotEqualTo(base);
        assertThat(ExportScopeHasher.hash(new ExportScope(List.of("cadastros.cliente"), null, null), "XLSX")).isNotEqualTo(base);
    }

    @Test
    void tokenTem43CaracteresUrlSafeESemRepeticao() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String t = DownloadTokenGenerator.gerar();
            assertThat(t).hasSize(43).matches("[A-Za-z0-9_-]{43}");
            assertThat(vistos.add(t)).isTrue();
        }
    }

    @Test
    void normalizarPreservaTiposSimplesEConverteOsDemais() {
        assertThat(ValorExport.normalizar(null)).isNull();
        assertThat(ValorExport.normalizar("x")).isEqualTo("x");
        assertThat(ValorExport.normalizar(true)).isEqualTo(true);
        assertThat(ValorExport.normalizar(42L)).isEqualTo(42L);
        assertThat(ValorExport.normalizar(new BigDecimal("1.50"))).isEqualTo(new BigDecimal("1.50"));
        assertThat(ValorExport.normalizar(Timestamp.from(Instant.parse("2026-10-11T12:00:00Z")))).isEqualTo("2026-10-11T12:00:00Z");
        assertThat(ValorExport.normalizar(Date.valueOf("2026-10-11"))).isEqualTo("2026-10-11");
        assertThat(ValorExport.normalizar(new byte[]{1, 2, 3})).isEqualTo("AQID");
    }

    @Test
    void longAcimaDe2Elevado53VirTexto() {
        assertThat(ValorExport.normalizar(Long.MAX_VALUE)).isEqualTo(String.valueOf(Long.MAX_VALUE));
    }

    @Test
    void paraTextoNaoUsaNotacaoCientifica() {
        assertThat(ValorExport.paraTexto(null)).isEmpty();
        assertThat(ValorExport.paraTexto(new BigDecimal("1E+3"))).isEqualTo("1000");
    }
}
