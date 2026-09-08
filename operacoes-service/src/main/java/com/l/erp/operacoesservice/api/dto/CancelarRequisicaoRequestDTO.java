package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Corpo de POST /requisicoes/{id}/cancelar — motivo obrigatório (spec/p2p-compras.md, Fase 1b). */
public record CancelarRequisicaoRequestDTO(@NotBlank String motivo) {
}
