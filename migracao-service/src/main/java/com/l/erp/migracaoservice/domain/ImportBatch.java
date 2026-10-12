package com.l.erp.migracaoservice.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Linha de migracao.import_batch (sem field_mapping: a Trilha 1 não usa). */
public record ImportBatch(UUID id, Long tenantId, UUID requestedBy, String entityType, String schemaVersion,
                          String status, String sourceTrack, String originalFileName, String rawFilePath,
                          Integer rowCount, String failureReason, OffsetDateTime createdAt,
                          OffsetDateTime updatedAt) {
}
