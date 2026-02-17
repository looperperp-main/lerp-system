package com.l.erp.authservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record TenantDTO(Long id,
                        @NotBlank
                        @Size(max = 120)
                        String name,
                        @NotBlank
                        @Pattern(regexp = "\\d{14}")
                        String cnpj,
                        @NotBlank
                        @Pattern(regexp = "ATIVO|PENDENTE|SUSPENSO|CANCELADO")
                        String status,
                        Instant creationDate,
                        String createdBy,
                        Instant updateDate,
                        String lastUpdatedBy) {
}
