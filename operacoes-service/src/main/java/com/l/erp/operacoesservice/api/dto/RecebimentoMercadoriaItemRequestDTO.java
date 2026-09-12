package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** Item do corpo de POST/PUT de recebimento (spec/p2p-compras.md, Fase 3). */
public record RecebimentoMercadoriaItemRequestDTO(
        @NotNull UUID pedidoItemId,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal quantidade,
        @NotNull @DecimalMin(value = "0.0") BigDecimal precoUnitarioNf
) {
}
