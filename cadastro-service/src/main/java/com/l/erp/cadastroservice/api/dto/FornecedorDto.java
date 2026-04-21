package com.l.erp.cadastroservice.api.dto;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * DTO for {@link com.l.erp.cadastroservice.domain.Fornecedor}
 */
public record FornecedorDto(UUID id, Long tenantId, UUID pessoaId, @NotNull Boolean ativo,
                            Instant createdAt, Instant updatedAt, UUID createdBy, UUID lastUpdatedBy,
                            Set<UUID> produtoFornecedorIds) implements Serializable {
}