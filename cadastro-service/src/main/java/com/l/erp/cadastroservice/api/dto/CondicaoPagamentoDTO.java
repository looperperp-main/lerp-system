package com.l.erp.cadastroservice.api.dto;

import java.time.Instant;
import java.util.UUID;

public record CondicaoPagamentoDTO(
        UUID id,
        Long tenantId,
        String nome,
        String descricao,
        Boolean ativo,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID lastUpdatedBy

) {
}
