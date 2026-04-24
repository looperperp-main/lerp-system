package com.l.erp.cadastroservice.api.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProdutoPrecoDTO(
        UUID id,
        Long tenantId,

        @NotNull(message = "A Tabela de Preço é obrigatória")
        UUID tabelaPrecoId,

        @NotNull(message = "O Preço é obrigatório")
        BigDecimal preco,

        @NotNull(message = "O Início de vigência é obrigatório")
        LocalDate inicioVigencia,

        LocalDate fimVigencia,

        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID lastUpdatedBy
) {}
