package com.l.erp.cadastroservice.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProdutoFornecedorDTO(
        UUID id,
        Long tenantId,

        @NotNull(message = "O fornecedor é obrigatório")
        UUID fornecedorId,

        @Size(max = 80)
        String codigoProdutoFornecedor,

        BigDecimal precoCusto,
        Integer leadTimeDias,

        @NotNull
        Boolean preferencial,

        @NotNull
        Boolean ativo,

        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID lastUpdatedBy
) {}
