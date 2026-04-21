package com.l.erp.cadastroservice.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for {@link com.l.erp.cadastroservice.domain.ProdutoCategoria}
 */
public record ProdutoCategoriaDTO(UUID id, Long tenantId, @NotNull @Size(max = 100) String nome,
                                  @Size(max = 500) String descricao, @NotNull Boolean ativa, Instant createdAt,
                                  Instant updatedAt, UUID createdBy,
                                  UUID lastUpdatedBy) implements Serializable {
}