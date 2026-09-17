package com.l.erp.operacoesservice.domain.estoque.enumerators;

public enum OrigemMovimentoEstoque {
    PEDIDO_VENDA, // vendas.pedido.id
    RECEBIMENTO,  // compras.recebimento_mercadoria.id
    AJUSTE,
    INVENTARIO,
    CONSUMO,      // [D7, §12] origem_id null — requisição de almoxarifado
    PRODUCAO      // [D11, §12] Fase 2 — origem_id = ordem_producao.id
}
