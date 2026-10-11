package com.l.erp.migracaoservice.util;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.Base64;

/** Converte o que o JDBC devolve em algo que CSV e XLSX escrevem sem perda e de forma reimportável. */
public final class ValorExport {

    // Acima de 2^53 o double do XLSX perde dígitos: esses inteiros saem como texto.
    private static final long LIMITE_DOUBLE_EXATO = 1L << 53;

    private ValorExport() {}

    /** Devolve null, Boolean, Number (seguro como double) ou String. */
    public static Object normalizar(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean || v instanceof String) return v;
        if (v instanceof Long l) return Math.abs(l) > LIMITE_DOUBLE_EXATO ? l.toString() : l;
        if (v instanceof Integer || v instanceof Short || v instanceof Double || v instanceof Float) return v;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Timestamp t) return t.toInstant().toString();   // timestamptz sai em UTC (ISO-8601)
        if (v instanceof Date d) return d.toLocalDate().toString();
        if (v instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
        return v.toString();   // UUID, time, jsonb, arrays do Postgres, enums
    }

    /** Texto para o CSV (null vira vazio; decimal sem notação científica). */
    public static String paraTexto(Object normalizado) {
        if (normalizado == null) return "";
        if (normalizado instanceof BigDecimal b) return b.toPlainString();
        return normalizado.toString();
    }
}
