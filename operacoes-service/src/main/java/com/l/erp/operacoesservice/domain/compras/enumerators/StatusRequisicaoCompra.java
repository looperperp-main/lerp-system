package com.l.erp.operacoesservice.domain.compras.enumerators;

/** Máquina de estados da requisição de compra (spec/p2p-compras.md §"Máquinas de estado"). Transições ficam na Fase 1b. */
public enum StatusRequisicaoCompra {
    RASCUNHO,
    PENDENTE_APROVACAO,
    APROVADA,
    REPROVADA,
    EM_COTACAO,
    ATENDIDA,
    CANCELADA
}
