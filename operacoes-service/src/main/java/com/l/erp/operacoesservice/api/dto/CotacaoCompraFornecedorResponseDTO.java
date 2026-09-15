package com.l.erp.operacoesservice.api.dto;

import com.l.erp.operacoesservice.domain.compras.enumerators.StatusCotacaoCompraFornecedor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Resposta de um fornecedor na cotação, já com {@code ordemSugerida} calculada pelo critério de
 * desempate (spec/p2p-compras.md, Fase 5): 1º menor preço líquido total (itens+frete), 2º menor
 * prazo de entrega, 3º melhor condição de pagamento, persistindo empate por maior prazo de
 * validade e, por fim, resposta mais recente. É só sugestão de ordenação — a escolha do vencedor
 * é sempre manual, feita pelo usuário no encerramento.
 */
public record CotacaoCompraFornecedorResponseDTO(
        UUID id,
        UUID fornecedorId,
        StatusCotacaoCompraFornecedor status,
        UUID condicaoPagamentoId,
        Integer prazoEntregaDias,
        BigDecimal valorFrete,
        String observacao,
        BigDecimal valorTotalOfertado,
        Integer ordemSugerida,
        List<CotacaoCompraFornecedorItemResponseDTO> itens
) {
}
