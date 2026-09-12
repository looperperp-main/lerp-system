package com.l.erp.operacoesservice.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Mesmo padrão de PedidoCompraItemResponseDTO (Fase 2). {@code produtoId} é denormalizado a
 * partir de {@code pedidoItem.produtoId} pelo Assembler — não existe campo direto na entidade. */
public record RecebimentoMercadoriaItemResponseDTO(
        UUID id,
        UUID pedidoItemId,
        UUID produtoId,
        BigDecimal quantidade,
        BigDecimal precoUnitarioNf
) {
}
