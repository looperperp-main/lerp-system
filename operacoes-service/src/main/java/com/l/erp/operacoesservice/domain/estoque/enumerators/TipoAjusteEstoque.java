package com.l.erp.operacoesservice.domain.estoque.enumerators;

// [D9, RN-EST-11, §12] substitui o motivo em texto livre; cada valor mapeia uma conta de
// contrapartida em contabil.mapeamento (financeiro-service — ainda não existe no código,
// integração fica pendente até o serviço existir).
public enum TipoAjusteEstoque {
    SALDO_INICIAL,
    INVENTARIO,
    AVARIA,
    PERDA_QUEBRA,
    FURTO_ROUBO,
    BONIFICACAO_RECEBIDA,
    AMOSTRA_BRINDE,
    ERRO_LANCAMENTO
}
