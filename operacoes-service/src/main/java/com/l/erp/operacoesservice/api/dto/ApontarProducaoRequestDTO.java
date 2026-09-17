package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Corpo de POST /api/v1/producao/ordens/{id}/apontar (spec/modulos/estoque/estoque.md §12, D11, Fase 2). */
public record ApontarProducaoRequestDTO(
        @NotNull BigDecimal quantidadeProduzida
) {
}
