package com.l.erp.migracaoservice.util;

import com.l.erp.migracaoservice.domain.ExportScope;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 do escopo normalizado: base do dedupe (spec export §5.2). */
public final class ExportScopeHasher {

    private ExportScopeHasher() {}

    /** Tabelas já vêm ordenadas e sem repetição; o formato entra porque XLSX e CSV são arquivos diferentes. */
    public static String hash(ExportScope escopo, String formato) {
        String canonico = String.join(",", escopo.tabelas())
                + "|" + escopo.dataInicial()
                + "|" + escopo.dataFinal()
                + "|" + formato;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
