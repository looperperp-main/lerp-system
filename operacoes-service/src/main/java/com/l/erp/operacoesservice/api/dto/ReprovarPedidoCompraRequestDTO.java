package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Mesmo padrão de ReprovarRequisicaoRequestDTO (Fase 1b) — motivo obrigatório (RN-P2P-09). */
public record ReprovarPedidoCompraRequestDTO(@NotBlank String motivo) {
}
