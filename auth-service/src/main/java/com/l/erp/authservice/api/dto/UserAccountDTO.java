package com.l.erp.authservice.api.dto;

import java.time.Instant;
import java.util.UUID;

public record UserAccountDTO(UUID id,
                             Long tenantId,
                             String email,
                             String passwordHash,
                             String displayName,
                             boolean active,
                             Instant lockedUntil,
                             Instant createdDate,
                             String createdBy,
                             Instant lastUpdateDate,
                             String lastUpdatedBy) {
}
