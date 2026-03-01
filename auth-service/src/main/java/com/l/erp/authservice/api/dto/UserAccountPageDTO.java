package com.l.erp.authservice.api.dto;

import java.time.Instant;
import java.util.UUID;

public record UserAccountPageDTO(UUID id,
                                 String tenantId,
                                 String email,
                                 String displayName,
                                 boolean active,
                                 Instant lockedUntil,
                                 Instant createdDate,
                                 String createdBy,
                                 Instant lastUpdateDate,
                                 String lastUpdatedBy) {
}
