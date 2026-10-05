package com.l.erp.emissaofiscalservice.services.nfse.adn;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Transporte do ADN: XML em UTF-8, compactado em GZip (não ZIP) e codificado em Base64. */
final class GzipBase64 {

    private GzipBase64() {
    }

    static String compactar(String xml) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(saida)) {
            gzip.write(xml.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao compactar o XML — " + e.getMessage(), e);
        }
        return Base64.getEncoder().encodeToString(saida.toByteArray());
    }

    static String descompactar(String base64) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(base64)))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Resposta do ADN não está em GZip/Base64 — " + e.getMessage(), e);
        }
    }
}
