package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Corpo de criação de cotação (spec/p2p-compras.md, Fase 5). {@code requisicaoId} é opcional —
 * cotação pode ser avulsa. Quando informado, os itens são copiados da requisição e {@code itens}
 * deste corpo é ignorado; quando avulsa, {@code itens} é obrigatório.
 */
public record CotacaoCompraRequestDTO(
        UUID requisicaoId,
        @NotNull UUID depositoId,
        LocalDate dataLimiteResposta,
        @NotEmpty List<UUID> fornecedorIds,
        @Valid List<CotacaoCompraItemRequestDTO> itens
) {
}
