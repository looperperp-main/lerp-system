package com.l.erp.operacoesservice.services.vendas;

import com.l.erp.operacoesservice.domain.vendas.Pedido;
import com.l.erp.operacoesservice.domain.vendas.PedidoItem;
import com.l.erp.operacoesservice.services.vendas.PedidoService.ParcelaFaturamento;

import java.util.List;

/**
 * Evento de domínio publicado por PedidoService.faturar() — consumido por PedidoEventListener
 * (AFTER_COMMIT) que delega pro PedidoEventProducer, tópico Constants.PEDIDO_FATURADO_TOPIC
 * (spec/o2c-vendas.md §8, Fase 5). Carrega parcelas pois o payload do evento inclui o
 * parcelamento gerado no faturamento.
 */
public record PedidoFaturadoEvent(Pedido pedido, List<PedidoItem> itens, List<ParcelaFaturamento> parcelas) {
}
