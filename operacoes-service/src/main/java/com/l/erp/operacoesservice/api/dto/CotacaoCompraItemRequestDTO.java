package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** Mesmo padrão de PedidoCompraItemRequestDTO (Fase 2), sem preço — cotação ainda não tem valor. */
public record CotacaoCompraItemRequestDTO(
        @NotNull UUID produtoId,
        @NotNull @DecimalMin("0.0001") BigDecimal quantidade
) {
}
