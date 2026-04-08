package com.l.erp.cadastroservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record DepositoDTO(
        UUID id,
        Long tenantId,
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        String nome,

        @Size(max = 500, message = "O nome deve ter no máximo 500 caracteres")
        String descricao,

        @Size(max = 30, message = "O nome deve ter no máximo 30 caracteres")
        String tipo,
        Boolean ativo,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID lastUpdatedBy
) {
}
