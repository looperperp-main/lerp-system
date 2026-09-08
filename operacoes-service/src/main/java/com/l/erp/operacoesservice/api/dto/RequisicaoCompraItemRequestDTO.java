package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Item da requisição de compra (spec/p2p-compras.md §"requisicao_compra_item", Fase 1b). */
public record RequisicaoCompraItemRequestDTO(
        @NotNull UUID produtoId,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal quantidade,
        @Size(max = 255) String observacao
) {
}
