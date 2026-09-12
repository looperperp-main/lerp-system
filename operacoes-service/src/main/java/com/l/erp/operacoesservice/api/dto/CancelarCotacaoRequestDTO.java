package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Mesmo padrão de CancelarPedidoCompraRequestDTO (Fase 2) — motivo obrigatório (RN-P2P-09). */
public record CancelarCotacaoRequestDTO(@NotBlank String motivo) {
}
