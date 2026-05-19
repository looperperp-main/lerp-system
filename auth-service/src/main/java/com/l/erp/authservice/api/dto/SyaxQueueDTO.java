package com.l.erp.authservice.api.dto;

import java.time.Instant;

public record SyaxQueueDTO(
        Long id,
        String tipo,
        String status,
        Long tenantId,
        String tenantName,
        String tenantCnpj,
        String tenantEmail,
        String payload,
        Instant createdAt,
        Instant updatedAt,
        String resolvedBy,
        String resolutionNotes
) {}