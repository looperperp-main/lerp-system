package com.l.erp.cadastroservice.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Projeção mínima de ProdutoFornecedor pro endpoint interno consumido pelo operacoes-service
 * (P2P — alerta de preço fora da faixa, spec/p2p-compras.md RN-P2P-04). Não confundir com
 * {@link ProdutoFornecedorDTO}, que é o DTO completo de CRUD aninhado em Produto.
 */
public record ProdutoFornecedorRefDTO(UUID produtoId, BigDecimal precoCusto) {
}
