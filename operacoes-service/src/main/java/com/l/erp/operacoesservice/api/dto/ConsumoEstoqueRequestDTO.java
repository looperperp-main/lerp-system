package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Corpo de POST /api/v1/estoque/consumo (spec/modulos/estoque/estoque.md §12, D7/E9). Requisição de
 * almoxarifado: quantidade informada diretamente (não é saldo contado, D5 é só para /ajustes).
 * {@code centroCustoId} obrigatório é validado no EstoqueService (RN-EST-10).
 */
public record ConsumoEstoqueRequestDTO(
        @NotNull UUID produtoId,
        @NotNull UUID depositoId,
        @NotNull BigDecimal quantidade,
        UUID centroCustoId,
        String motivo,
        // RN-EST-12 [D10, §12]: só tem efeito pra REVENDA/USO_CONSUMO/MATERIA_PRIMA — permite o consumo
        // deixar o saldo negativo e abre pendência de regularização (nunca silenciosa).
        boolean permitirSaldoNegativo
) {
}
