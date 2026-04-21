package com.l.erp.cadastroservice.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CondicaoPagamentoDTO(
        UUID id,
        Long tenantId,
        String nome,
        String descricao,
        @NotNull(message = "O Campo Ativo é obrigatório")
        Boolean ativo,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID lastUpdatedBy

) {
}
