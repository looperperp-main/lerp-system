package com.l.erp.operacoesservice.api.dto;

import com.l.erp.operacoesservice.domain.vendas.enumerators.TipoItemPedido;

import java.math.BigDecimal;
import java.util.UUID;

/** Item do pedido na resposta (aninhado em PedidoResponseDTO.itens, spec §5/§10, Fase 4). */
public record PedidoItemResponseDTO(
        UUID id,
        UUID produtoId,
        TipoItemPedido tipoItem,
        BigDecimal quantidade,
        BigDecimal precoUnitario,
        BigDecimal desconto,
        BigDecimal valorTotal,
        BigDecimal precoTabela,
        Boolean precoManual,
        UUID tabelaPrecoId,
        String origemPreco
) {
}
