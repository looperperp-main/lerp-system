package com.l.erp.operacoesservice.api.dto;

import com.l.erp.operacoesservice.domain.vendas.enumerators.ModalidadeFrete;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Corpo de criação (POST) e edição (PUT) do orçamento (spec/o2c-vendas.md §5/§10, Fase 4). */
public record PedidoRequestDTO(
        @NotNull UUID clienteId,
        LocalDate dataEmissao,
        LocalDate dataValidade,
        UUID vendedorId,
        UUID condicaoPagamentoId,
        ModalidadeFrete modalidadeFrete,
        String observacao,
        @NotEmpty @Valid List<PedidoItemRequestDTO> itens
) {
}
