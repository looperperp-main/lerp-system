package com.l.erp.operacoesservice.domain.compras.enumerators;

/** Máquina de estados da cotação de compra (spec/p2p-compras.md, Fase 5). */
public enum StatusCotacaoCompra {
    ABERTA,
    ENCERRADA,
    CANCELADA
}
