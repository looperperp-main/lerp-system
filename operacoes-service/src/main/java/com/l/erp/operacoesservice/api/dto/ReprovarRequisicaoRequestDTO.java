package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Corpo de POST /requisicoes/{id}/reprovar — motivo obrigatório (spec/p2p-compras.md, Fase 1b). */
public record ReprovarRequisicaoRequestDTO(@NotBlank String motivo) {
}
