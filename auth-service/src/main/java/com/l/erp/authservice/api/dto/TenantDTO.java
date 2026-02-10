package com.l.erp.authservice.api.dto;

import java.time.Instant;

public record TenantDTO(Long id,
                        String name,
                        String cnpj,
                        String status,
                        Instant creationDate,
                        String createdBy,
                        Instant updateDate,
                        String lastUpdatedBy) {
}
