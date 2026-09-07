package com.l.erp.operacoesservice.domain.estoque.enumerators;

public enum TipoMovimentoEstoque {
    ENTRADA_COMPRA,          // (+) recebimento confirmado (P2P, Fase 3 do p2p-compras.md)
    ESTORNO_ENTRADA_COMPRA,  // (-) recebimento CONFIRMADO cancelado
    SAIDA_VENDA,             // (-) expedição do pedido de venda (O2C)
    ESTORNO_SAIDA_VENDA,     // (+) cancelamento de pedido que estava EXPEDIDO
    AJUSTE_ENTRADA,
    AJUSTE_SAIDA
}
