package com.l.erp.cadastroservice.domain.enumerators;

/**
 * Indicador da IE do destinatário (grupo {@code dest} da NF-e) —
 * spec/modulos/emissao-fiscal/emissao-fiscal.md §10. {@code codigo} é o valor que vai pro XML.
 */
public enum IndicadorIeDestinatario {
    CONTRIBUINTE_ICMS(1),
    ISENTO(2),
    NAO_CONTRIBUINTE(9);

    private final int codigo;

    IndicadorIeDestinatario(int codigo) {
        this.codigo = codigo;
    }

    public int getCodigo() {
        return codigo;
    }
}
