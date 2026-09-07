package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Corpo de POST /api/v1/pedidos/{id}/cancelar (spec/o2c-vendas.md §5/§10, Fase 4). */
public record CancelarPedidoRequestDTO(@NotBlank String motivo) {
}
