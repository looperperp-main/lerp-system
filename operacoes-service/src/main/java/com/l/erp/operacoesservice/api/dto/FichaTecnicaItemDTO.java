package com.l.erp.operacoesservice.api.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** Componente + quantidade (pra produzir 1 unidade do produto acabado) de uma ficha técnica (§12, D11). */
public record FichaTecnicaItemDTO(
        @NotNull UUID produtoComponenteId,
        @NotNull BigDecimal quantidade
) {
}
