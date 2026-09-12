package com.l.erp.operacoesservice.services.compras;

import com.l.erp.operacoesservice.domain.compras.PedidoCompra;
import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoria;
import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoriaItem;

import java.util.List;

/**
 * Evento de domínio publicado por RecebimentoMercadoriaService.confirmar() — consumido por
 * RecebimentoEventListener (AFTER_COMMIT) que delega pro RecebimentoEventProducer, tópico
 * Constants.RECEBIMENTO_CONFIRMADO_TOPIC (spec/p2p-compras.md §"Integração com estoque", Fase 3).
 * Só notificação externa (BI, ultimo_preco_compra) — a baixa real de estoque já commitou junto
 * com o recebimento, na mesma transação (chamada in-process ao EstoqueService).
 */
public record RecebimentoConfirmadoEvent(RecebimentoMercadoria recebimento, PedidoCompra pedido,
                                          List<RecebimentoMercadoriaItem> itens) {
}
