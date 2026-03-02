package com.l.erp.authservice.api.dto;

import java.time.Instant;
import java.util.UUID;

public record RoleDTO(
        UUID id,
        String name,
        Long tenantId,
        Instant createdDate,
        String createdBy,
        Instant lastUpdateDate,
        String lastUpdateBy
) {
}
