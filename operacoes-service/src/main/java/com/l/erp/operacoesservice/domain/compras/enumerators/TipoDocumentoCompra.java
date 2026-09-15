package com.l.erp.operacoesservice.domain.compras.enumerators;

/** Tipo de documento numerado por compras.compra_numeracao / discriminador de compra_status_historico.documento_tipo. */
public enum TipoDocumentoCompra {
    REQUISICAO,
    COTACAO,
    PEDIDO,
    RECEBIMENTO
}
