package com.l.erp.operacoesservice.api.dto;

import com.l.erp.operacoesservice.domain.estoque.enumerators.OrigemMovimentoEstoque;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Corpo de POST /api/v1/estoque/ajustes (spec/estoque.md §5.3, D5). {@code quantidadeContada} é o
 * saldo que de fato existe na prateleira, não a diferença — o service calcula o delta e escolhe
 * AJUSTE_ENTRADA/AJUSTE_SAIDA. {@code motivo} obrigatório e {@code quantidadeContada >= 0} são
 * validados no EstoqueService (mensagens PT-BR já em Constants), não repetidos aqui.
 */
public record AjusteEstoqueRequestDTO(
        @NotNull UUID produtoId,
        @NotNull UUID depositoId,
        @NotNull BigDecimal quantidadeContada,
        @NotNull OrigemMovimentoEstoque origem,
        String motivo,
        BigDecimal valorUnitario
) {
}
