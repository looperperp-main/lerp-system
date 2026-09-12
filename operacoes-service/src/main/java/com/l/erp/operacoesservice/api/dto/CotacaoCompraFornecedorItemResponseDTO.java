package com.l.erp.operacoesservice.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Preço unitário ofertado pelo fornecedor pra um item da cotação (spec/p2p-compras.md, Fase 5). */
public record CotacaoCompraFornecedorItemResponseDTO(
        UUID cotacaoItemId,
        UUID produtoId,
        BigDecimal quantidade,
        BigDecimal precoUnitario,
        BigDecimal valorTotal
) {
}
