package com.l.erp.operacoesservice.api.dto;

import com.l.erp.operacoesservice.domain.estoque.enumerators.OrigemMovimentoEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.TipoAjusteEstoque;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Corpo de POST /api/v1/estoque/ajustes (spec/modulos/estoque/estoque.md §5.3/§12, D5/D9). {@code quantidadeContada}
 * é o saldo que de fato existe na prateleira, não a diferença — o service calcula o delta e escolhe
 * AJUSTE_ENTRADA/AJUSTE_SAIDA. {@code tipoAjuste} obrigatório, {@code quantidadeContada >= 0} e o
 * custo obrigatório em entrada fora de SALDO_INICIAL (RN-EST-11) são validados no EstoqueService
 * (mensagens PT-BR já em Constants), não repetidos aqui. {@code motivo} virou observação livre
 * opcional (complementar a tipoAjuste); {@code documentoReferencia} é o lastro documental opcional.
 */
public record AjusteEstoqueRequestDTO(
        @NotNull UUID produtoId,
        @NotNull UUID depositoId,
        @NotNull BigDecimal quantidadeContada,
        @NotNull OrigemMovimentoEstoque origem,
        TipoAjusteEstoque tipoAjuste,
        String motivo,
        @Size(max = 200) String documentoReferencia,
        BigDecimal valorUnitario,
        // RN-EST-12 [D10, §12]: só tem efeito pra REVENDA/USO_CONSUMO/MATERIA_PRIMA — permite o ajuste
        // deixar o saldo negativo e abre pendência de regularização (nunca silenciosa).
        boolean permitirSaldoNegativo
) {
}
