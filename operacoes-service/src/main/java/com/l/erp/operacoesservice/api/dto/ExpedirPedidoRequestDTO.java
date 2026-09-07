package com.l.erp.operacoesservice.api.dto;

import com.l.erp.operacoesservice.domain.vendas.enumerators.ModalidadeFrete;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** Corpo de POST /api/v1/pedidos/{id}/expedir (spec/o2c-vendas.md §5/§10, Fase 4). */
public record ExpedirPedidoRequestDTO(
        @NotNull UUID depositoId,
        UUID transportadoraId,
        BigDecimal valorFrete,
        ModalidadeFrete modalidadeFrete
) {
}
