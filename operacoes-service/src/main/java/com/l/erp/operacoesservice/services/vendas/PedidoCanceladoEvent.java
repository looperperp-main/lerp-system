package com.l.erp.operacoesservice.services.vendas;

import com.l.erp.operacoesservice.domain.vendas.Pedido;
import com.l.erp.operacoesservice.domain.vendas.PedidoItem;

import java.util.List;

/**
 * Evento de domínio publicado por PedidoService.cancelar() — consumido por PedidoEventListener
 * (AFTER_COMMIT) que delega pro PedidoEventProducer, tópico Constants.PEDIDO_CANCELADO_TOPIC
 * (spec/o2c-vendas.md §8, Fase 5).
 */
public record PedidoCanceladoEvent(Pedido pedido, List<PedidoItem> itens) {
}
