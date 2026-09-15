package com.l.erp.operacoesservice.domain.compras.enumerators;

/** Máquina de estados do pedido de compra (spec/p2p-compras.md, Fase 2). */
public enum StatusPedidoCompra {
    RASCUNHO,
    PENDENTE_APROVACAO,
    APROVADO,
    REPROVADO,
    ENVIADO,
    RECEBIDO_PARCIAL,
    RECEBIDO_TOTAL,
    ENCERRADO,
    CANCELADO
}
