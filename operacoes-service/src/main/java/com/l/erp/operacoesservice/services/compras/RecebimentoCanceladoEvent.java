package com.l.erp.operacoesservice.services.compras;

import com.l.erp.operacoesservice.domain.compras.PedidoCompra;
import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoria;

/**
 * Evento de domínio publicado por RecebimentoMercadoriaService.cancelar() quando o recebimento
 * cancelado estava CONFIRMADO (o estorno de estoque já commitou junto, na mesma transação) —
 * consumido por RecebimentoEventListener (AFTER_COMMIT), tópico
 * Constants.RECEBIMENTO_CANCELADO_TOPIC. Só notificação externa (spec/p2p-compras.md, Fase 3).
 */
public record RecebimentoCanceladoEvent(RecebimentoMercadoria recebimento, PedidoCompra pedido) {
}
