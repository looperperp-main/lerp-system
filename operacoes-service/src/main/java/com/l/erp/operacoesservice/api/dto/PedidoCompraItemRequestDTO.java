package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** Item do corpo de criação/edição de pedido de compra (spec/p2p-compras.md, Fase 2). */
public record PedidoCompraItemRequestDTO(
        @NotNull UUID produtoId,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal quantidade,
        @NotNull @DecimalMin(value = "0.0") BigDecimal precoUnitario
) {
}
