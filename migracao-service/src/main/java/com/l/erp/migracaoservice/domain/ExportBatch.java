package com.l.erp.migracaoservice.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Linha de migracao.export_batch (JSONB chega como String; quem precisa faz o parse). */
public record ExportBatch(UUID id, Long tenantId, UUID requestedBy, String scopeJson, String format,
                          String status, String filePathsJson, String tableCountsJson, int attemptCount,
                          String reason, String failureReason, OffsetDateTime createdAt,
                          OffsetDateTime readyAt, OffsetDateTime expiresAt, OffsetDateTime downloadedAt) {
}
