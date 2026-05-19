package com.l.erp.authservice.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateSyaxQueueStatusRequest(
        @NotBlank String status,
        String resolvedBy,
        String resolutionNotes
) {}