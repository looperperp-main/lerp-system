package com.l.erp.emissaofiscalservice.api.dto;

import com.l.erp.emissaofiscalservice.domain.CredenciamentoSefaz;
import com.l.erp.emissaofiscalservice.domain.StatusCredenciamento;
import com.l.erp.emissaofiscalservice.domain.TipoDocumentoFiscal;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CredenciamentoSefazResponseDTO(
        UUID id,
        UUID emitenteId,
        String uf,
        TipoDocumentoFiscal modelo,
        StatusCredenciamento status,
        OffsetDateTime dataCredenciamento
) {
    public static CredenciamentoSefazResponseDTO from(CredenciamentoSefaz entidade) {
        return new CredenciamentoSefazResponseDTO(
                entidade.getId(),
                entidade.getEmitenteId(),
                entidade.getUf(),
                entidade.getModelo(),
                entidade.getStatus(),
                entidade.getDataCredenciamento());
    }
}
