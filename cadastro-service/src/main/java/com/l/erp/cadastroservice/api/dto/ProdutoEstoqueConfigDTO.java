package com.l.erp.cadastroservice.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProdutoEstoqueConfigDTO(
        UUID id,
        Long tenantId,
        UUID depositoId,
        UUID fornecedorPreferencialId,
        BigDecimal estoqueMinimo,
        BigDecimal estoqueMaximo,
        BigDecimal pontoReposicao,
        Integer leadTimeDias,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID lastUpdatedBy
) {}
