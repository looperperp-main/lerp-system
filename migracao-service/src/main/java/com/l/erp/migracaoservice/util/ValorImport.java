package com.l.erp.migracaoservice.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Conversão de texto de célula para tipo: usada pela validação agora e pela promoção depois. */
public final class ValorImport {

    // STRICT: no modo padrão (SMART) "31/02/2026" viraria 28/02 em silêncio.
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final Set<String> VERDADEIRO = Set.of("SIM", "S", "TRUE", "VERDADEIRO", "1");
    private static final Set<String> FALSO = Set.of("NAO", "N", "FALSE", "FALSO", "0");

    private ValorImport() {}

    /** Célula em branco vira null (coluna opcional sem valor); o resto vem sem espaços nas pontas. */
    public static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    /** Aceita AAAA-MM-DD (ISO, como o export e o XLSX do SYAX gravam) e DD/MM/AAAA. */
    public static Optional<LocalDate> data(String texto) {
        String t = texto.strip();
        try {
            return Optional.of(t.contains("/") ? LocalDate.parse(t, DATA_BR) : LocalDate.parse(t));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** SIM/NAO (com ou sem acento), S/N, TRUE/FALSE, 1/0. */
    public static Optional<Boolean> booleano(String texto) {
        String t = texto.strip().toUpperCase(Locale.ROOT).replace("Ã", "A");
        if (VERDADEIRO.contains(t)) return Optional.of(true);
        if (FALSO.contains(t)) return Optional.of(false);
        return Optional.empty();
    }

    /** Aceita vírgula ou ponto decimal; não aceita separador de milhar. */
    public static Optional<BigDecimal> decimal(String texto) {
        try {
            return Optional.of(new BigDecimal(texto.strip().replace(',', '.')));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
