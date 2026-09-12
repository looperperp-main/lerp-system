package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Corpo de registro da resposta de um fornecedor (spec/p2p-compras.md, Fase 5). Digitado pelo
 * comprador a partir da cotação recebida do fornecedor — {@code condicaoPagamentoId} é obrigatória
 * (RN-P2P-03).
 */
public record CotacaoCompraRespostaRequestDTO(
        @NotNull UUID condicaoPagamentoId,
        Integer prazoEntregaDias,
        BigDecimal valorFrete,
        @Size(max = 500) String observacao,
        @NotEmpty @Valid List<CotacaoCompraRespostaItemRequestDTO> itens
) {
}
