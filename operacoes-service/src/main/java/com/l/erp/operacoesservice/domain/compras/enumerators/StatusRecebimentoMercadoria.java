package com.l.erp.operacoesservice.domain.compras.enumerators;

/** Máquina de estados do recebimento de mercadoria (spec/p2p-compras.md, Fase 3). FATURADO é
 * terminal e pertence à Fase 4 (faturamento) — o enum já nasce completo pra não exigir ALTER
 * depois, mas nenhuma transição para FATURADO é exercitada nesta fase. */
public enum StatusRecebimentoMercadoria {
    EM_CONFERENCIA,
    CONFIRMADO,
    FATURADO,
    CANCELADO
}
