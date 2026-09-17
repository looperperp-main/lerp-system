package com.l.erp.operacoesservice.domain.estoque.enumerators;

// [D10, RN-EST-12, §12] gerada quando um movimento sujeito a bloqueio deixa o saldo negativo.
public enum PendenciaTipoEstoque {
    REGULARIZACAO,     // REVENDA/USO_CONSUMO/MATERIA_PRIMA com override explícito (permitirSaldoNegativo)
    APONTAR_PRODUCAO   // PRODUTO_ACABADO — sempre permitido, sempre pendente de apontamento de produção
}
