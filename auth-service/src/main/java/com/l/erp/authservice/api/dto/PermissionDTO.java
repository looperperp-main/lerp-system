package com.l.erp.authservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record PermissionDTO(
        UUID id,
        @NotBlank(message = "O código é obrigatório")
        @Size(max = 120, message = "O código deve ter no máximo 120 caracteres")
        String code,
        @NotBlank(message = "O domínio é obrigatório")
        @Size(max = 60, message = "O domínio deve ter no máximo 60 caracteres")
        String domain,
        @NotBlank(message = "A descrição é obrigatória")
        @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres")
        String description,
        Instant createdDate,
        String createdBy,
        Instant lastUpdateDate,
        String lastUpdatedBy
) {
}
