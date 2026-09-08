package com.l.erp.operacoesservice.domain.estoque.enumerators;

public enum OrigemMovimentoEstoque {
    PEDIDO_VENDA, // vendas.pedido.id
    RECEBIMENTO,  // compras.recebimento_mercadoria.id
    AJUSTE,
    INVENTARIO
}
