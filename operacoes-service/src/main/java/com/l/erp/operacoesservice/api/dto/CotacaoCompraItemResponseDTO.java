package com.l.erp.operacoesservice.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Mesmo padrão de PedidoCompraItemResponseDTO (Fase 2). */
public record CotacaoCompraItemResponseDTO(
        UUID id,
        UUID produtoId,
        BigDecimal quantidade
) {
}
