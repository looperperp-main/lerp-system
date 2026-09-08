package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Corpo de criação (POST) e edição (PUT) da requisição (spec/p2p-compras.md §"requisicao_compra", Fase 1b). */
public record RequisicaoCompraRequestDTO(
        @NotNull UUID solicitanteId,
        UUID depositoId,
        @Size(max = 500) String justificativa,
        LocalDate dataNecessidade,
        @NotEmpty @Valid List<RequisicaoCompraItemRequestDTO> itens
) {
}
