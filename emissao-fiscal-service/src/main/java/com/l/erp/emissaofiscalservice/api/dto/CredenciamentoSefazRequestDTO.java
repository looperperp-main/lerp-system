package com.l.erp.emissaofiscalservice.api.dto;

import com.l.erp.emissaofiscalservice.domain.StatusCredenciamento;
import com.l.erp.emissaofiscalservice.domain.TipoDocumentoFiscal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CredenciamentoSefazRequestDTO(
        @NotBlank @Size(min = 2, max = 2) String uf,
        @NotNull TipoDocumentoFiscal modelo,
        @NotNull StatusCredenciamento status
) {
}
