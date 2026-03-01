package com.l.erp.authservice.api.dto.audit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AuditLogDTO(
        UUID id,
        @NotNull
        UUID actorUserId,
        @Size(max = 80)
        @NotNull
        String action,
        @Size(max = 60)
        String targetType,
        UUID targetId,
        @Size(max = 20)
        @NotNull
        String result,
        String detailsJson,
        UUID correlationId
) {
}
