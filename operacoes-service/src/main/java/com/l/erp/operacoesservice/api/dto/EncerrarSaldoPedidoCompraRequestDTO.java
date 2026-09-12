package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Encerra o saldo pendente do pedido (RECEBIDO_PARCIAL/RECEBIDO_TOTAL -> ENCERRADO) quando o
 * comprador decide não receber o restante — motivo obrigatório, mesmo padrão de
 * CancelarPedidoCompraRequestDTO (spec/p2p-compras.md, endpoint reservado desde a Fase 2,
 * habilitado na Fase 3 porque só faz sentido depois que existe recebimento). */
public record EncerrarSaldoPedidoCompraRequestDTO(@NotBlank String motivo) {
}
