package com.l.erp.cadastroservice.domain.enumerators;

// D6 (spec/modulos/estoque/estoque.md §12) — para que serve o produto (alimenta fiscal,
// contábil e a política de saldo negativo por finalidade, ainda não implementada).
public enum FinalidadeProduto {
    REVENDA,
    USO_CONSUMO,
    MATERIA_PRIMA,
    PRODUTO_ACABADO
}
