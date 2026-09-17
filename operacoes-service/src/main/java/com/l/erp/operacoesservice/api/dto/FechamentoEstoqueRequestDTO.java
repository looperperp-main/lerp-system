package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Corpo de POST /api/v1/estoque/fechamento (spec/modulos/estoque/estoque.md §12, RN-EST-13). */
public record FechamentoEstoqueRequestDTO(
        @NotBlank String competencia // "aaaa-mm"
) {
}
