package com.l.erp.emissaofiscalservice.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Item da nota (grupo I) + o snapshot fiscal já resolvido (grupos N/Q/S/UB via {@link SnapshotFiscalItemDTO}). */
public record ItemDocumentoDTO(
        @NotBlank @Size(max = 60) String codigo,
        @NotBlank @Size(max = 120) String descricao,
        @NotBlank @Pattern(regexp = "\\d{8}", message = "NCM deve ter 8 dígitos") String ncm,
        @NotBlank @Pattern(regexp = "[0-8]", message = "origem deve ser de 0 a 8") String origem,
        @NotBlank @Size(max = 6) String unidadeComercial,
        @NotNull @Positive BigDecimal quantidade,
        @NotNull @PositiveOrZero BigDecimal valorUnitario,
        @NotNull @PositiveOrZero BigDecimal valorTotal,
        @NotNull @Valid SnapshotFiscalItemDTO fiscal
) {
}
