package com.l.erp.cadastroservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VendedorDTO(
        UUID id,
        Long tenantId,
        UUID pessoaId,

        @NotBlank(message = "O nome é obrigatório")
        String nome,

        @PositiveOrZero(message = "A comissão não pode ser negativa")
        BigDecimal comissaoPercentual,

        Boolean ativo,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID lastUpdatedBy
) {
}
