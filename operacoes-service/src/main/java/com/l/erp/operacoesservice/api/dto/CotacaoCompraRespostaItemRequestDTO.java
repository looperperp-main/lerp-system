package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** Preço unitário digitado pelo comprador pra um item, ao registrar a resposta do fornecedor. */
public record CotacaoCompraRespostaItemRequestDTO(
        @NotNull UUID cotacaoItemId,
        @NotNull @DecimalMin("0.0") BigDecimal precoUnitario
) {
}
