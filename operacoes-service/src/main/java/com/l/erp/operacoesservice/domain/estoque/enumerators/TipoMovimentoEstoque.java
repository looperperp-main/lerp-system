package com.l.erp.operacoesservice.domain.estoque.enumerators;

public enum TipoMovimentoEstoque {
    ENTRADA_COMPRA,          // (+) recebimento confirmado (P2P, Fase 3 do p2p-compras.md)
    ESTORNO_ENTRADA_COMPRA,  // (-) recebimento CONFIRMADO cancelado
    SAIDA_VENDA,             // (-) expedição do pedido de venda (O2C)
    ESTORNO_SAIDA_VENDA,     // (+) cancelamento de pedido que estava EXPEDIDO
    AJUSTE_ENTRADA,
    AJUSTE_SAIDA,
    SAIDA_CONSUMO,           // (-) [D7, §12] requisição de almoxarifado, centro_custo_id obrigatório (RN-EST-10)
    SAIDA_PRODUCAO,          // (-) [D11, §12] Fase 2 — consumo de componente ao apontar produção
    ENTRADA_PRODUCAO         // (+) [D11, §12] Fase 2 — entrada do produto acabado ao apontar produção
}
