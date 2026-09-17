package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Corpo de POST /api/v1/producao/fichas-tecnicas (spec/modulos/estoque/estoque.md §12, D11, Fase 2). */
public record FichaTecnicaRequestDTO(
        @NotNull UUID produtoAcabadoId,
        @NotEmpty List<FichaTecnicaItemDTO> itens
) {
}
