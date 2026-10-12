package com.l.erp.migracaoservice.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Único ponto que conhece o diretório dos arquivos originais do import. Mesma regra do export:
 * local usa o temp dir; no OCI o valor de SYAX_IMPORT_DIR aponta para o volume nomeado.
 */
@ConfigurationProperties(prefix = "syax.import")
public record ImportProperties(Path baseDir, int retentionDays) {
}
