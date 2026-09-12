package com.l.erp.operacoesservice.domain.compras.enumerators;

/** Tipo de documento fiscal do recebimento (spec/p2p-compras.md §"recebimento_mercadoria",
 * Fase 3, Rev. 5). Define quais campos NF (nfe_*) ou NFS-e (nfse_codigo_verificacao) são
 * obrigatórios — validado em RecebimentoMercadoriaService (RN-P2P-07). */
public enum TipoDocumentoFiscal {
    NFE,
    NFSE
}
