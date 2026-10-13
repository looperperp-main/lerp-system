package com.l.erp.migracaoservice.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Linha de migracao.import_batch (sem field_mapping: só a Trilha 2 o lê, por consulta própria). */
public record ImportBatch(UUID id, Long tenantId, UUID requestedBy, String entityType, String schemaVersion,
                          String status, String sourceTrack, String originalFileName, String rawFilePath,
                          Integer rowCount, String failureReason, OffsetDateTime createdAt,
                          OffsetDateTime updatedAt, OffsetDateTime conciergeDeadlineAt, int conciergeRejections) {

    /** Batch fora da Trilha 3: sem prazo de concierge e sem recusas. */
    public ImportBatch(UUID id, Long tenantId, UUID requestedBy, String entityType, String schemaVersion,
                       String status, String sourceTrack, String originalFileName, String rawFilePath,
                       Integer rowCount, String failureReason, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this(id, tenantId, requestedBy, entityType, schemaVersion, status, sourceTrack, originalFileName, rawFilePath,
                rowCount, failureReason, createdAt, updatedAt, null, 0);
    }
}
