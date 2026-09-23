package com.l.erp.emissaofiscalservice.api.dto;

import com.l.erp.emissaofiscalservice.domain.CertificadoDigital;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CertificadoDigitalResponseDTO(
        UUID id,
        UUID estabelecimentoId,
        String cnpjSubject,
        OffsetDateTime certificadoValidoAte,
        boolean ativo
) {
    public static CertificadoDigitalResponseDTO from(CertificadoDigital entidade) {
        return new CertificadoDigitalResponseDTO(
                entidade.getId(),
                entidade.getEstabelecimentoId(),
                entidade.getCnpjSubject(),
                entidade.getCertificadoValidoAte(),
                entidade.isAtivo());
    }
}
