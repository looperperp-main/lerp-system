package com.l.erp.operacoesservice.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Mesmo padrão de RequisicaoCompraItemResponseDTO (Fase 1b). */
public record PedidoCompraItemResponseDTO(
        UUID id,
        UUID produtoId,
        BigDecimal quantidade,
        BigDecimal precoUnitario,
        BigDecimal quantidadeRecebida,
        BigDecimal valorTotal
) {
}
