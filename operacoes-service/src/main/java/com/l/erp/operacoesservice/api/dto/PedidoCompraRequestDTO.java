package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Corpo de criação/edição de pedido de compra (spec/p2p-compras.md, Fase 2). {@code requisicaoId} é
 * opcional — pedido pode ser avulso ou originado de uma requisição de compra aprovada.
 */
public record PedidoCompraRequestDTO(
        @NotNull UUID fornecedorId,
        @NotNull UUID condicaoPagamentoId,
        @NotNull UUID depositoId,
        UUID requisicaoId,
        LocalDate dataPrevisaoEntrega,
        BigDecimal valorFrete,
        @Size(max = 500) String observacao,
        @NotEmpty @Valid List<PedidoCompraItemRequestDTO> itens
) {
}
