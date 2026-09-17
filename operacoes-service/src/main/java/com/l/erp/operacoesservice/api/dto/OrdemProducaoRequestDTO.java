package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** Corpo de POST /api/v1/producao/ordens (spec/modulos/estoque/estoque.md §12, D11, Fase 2). */
public record OrdemProducaoRequestDTO(
        @NotNull UUID produtoAcabadoId,
        @NotNull BigDecimal quantidadePlanejada,
        @NotNull UUID depositoId
) {
}
