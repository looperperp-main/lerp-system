package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** Item do orçamento (spec/o2c-vendas.md §5/§10, Fase 4). precoUnitario nulo = motor de preço (§6, ainda stub). */
public record PedidoItemRequestDTO(
        @NotNull UUID produtoId,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal quantidade,
        BigDecimal precoUnitario,
        BigDecimal desconto
) {
}
