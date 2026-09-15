package com.l.erp.cadastroservice.domain.enumerators;

/**
 * Código de Regime Tributário do emitente (grupo {@code emit} da NF-e) —
 * spec/modulos/emissao-fiscal/emissao-fiscal.md §10. {@code codigo} é o valor que vai pro XML.
 */
public enum CodigoRegimeTributario {
    SIMPLES_NACIONAL(1),
    SIMPLES_EXCESSO(2),
    REGIME_NORMAL(3),
    MEI(4);

    private final int codigo;

    CodigoRegimeTributario(int codigo) {
        this.codigo = codigo;
    }

    public int getCodigo() {
        return codigo;
    }
}
