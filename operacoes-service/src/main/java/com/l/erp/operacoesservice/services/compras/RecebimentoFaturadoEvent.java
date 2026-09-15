package com.l.erp.operacoesservice.services.compras;

import com.l.erp.operacoesservice.domain.compras.PedidoCompra;
import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoria;
import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoriaItem;

import java.util.List;

/**
 * Evento de domínio publicado por RecebimentoMercadoriaService.faturar() — consumido por
 * RecebimentoEventListener (AFTER_COMMIT) que delega pro RecebimentoEventProducer, tópico
 * Constants.NFE_ENTRADA_APROVADA_TOPIC (spec/p2p-compras.md §"Integração com o financeiro",
 * Fase 4). Único ponto do P2P que gera título a pagar — o financeiro-service consome o evento.
 */
public record RecebimentoFaturadoEvent(RecebimentoMercadoria recebimento, PedidoCompra pedido,
                                        List<RecebimentoMercadoriaItem> itens,
                                        List<RecebimentoMercadoriaService.ParcelaFaturamentoCompra> parcelas) {
}
