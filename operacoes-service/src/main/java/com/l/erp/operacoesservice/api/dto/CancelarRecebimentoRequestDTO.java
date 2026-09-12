package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.constraints.Size;

/** Motivo opcional — spec/p2p-compras.md não exige motivo obrigatório pra cancelar recebimento
 * (diferente de CancelarPedidoCompraRequestDTO, onde é obrigatório). */
public record CancelarRecebimentoRequestDTO(@Size(max = 500) String motivo) {
}
