package com.l.erp.migracaoservice.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Único ponto que conhece o diretório dos arquivos de export. Local: temp dir da máquina; OCI:
 * /var/syax/exports (volume nomeado) — a diferença é só o valor de SYAX_EXPORT_DIR.
 */
@ConfigurationProperties(prefix = "syax.export")
public record ExportProperties(Path baseDir, int retentionDays) {
}
