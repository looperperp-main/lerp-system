package com.l.erp.operacoesservice.domain.vendas.enumerators;

/**
 * Último passo conhecido da emissão fiscal do pedido faturado. O desfecho na SEFAZ (autorizada,
 * rejeitada) vive no emissao-fiscal-service e se consulta pelo {@code documentoFiscalId}; aqui só se
 * sabe se o documento chegou lá.
 */
public enum StatusEmissaoPedido {
    ENVIADO,
    FALHA_ENVIO
}
