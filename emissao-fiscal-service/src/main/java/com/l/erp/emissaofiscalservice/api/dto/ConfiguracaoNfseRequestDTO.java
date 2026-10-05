package com.l.erp.emissaofiscalservice.api.dto;

import com.l.erp.emissaofiscalservice.domain.CodigoProvedorNfse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code provedor} omitido = ADN (padrão da spec §4.4). */
public record ConfiguracaoNfseRequestDTO(
        @NotBlank @Pattern(regexp = "\\d{7}", message = "deve ter 7 dígitos (código IBGE)") String municipioIbge,
        CodigoProvedorNfse provedor,
        @Size(min = 1, max = 15) String inscricaoMunicipal
) {
}
