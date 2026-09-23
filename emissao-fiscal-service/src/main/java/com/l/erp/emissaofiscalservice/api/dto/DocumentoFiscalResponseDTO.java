package com.l.erp.emissaofiscalservice.api.dto;

import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;

import java.util.UUID;

public record DocumentoFiscalResponseDTO(
        UUID id,
        String documento,
        String serie,
        long numero,
        StatusDocumentoFiscal status,
        String chaveAcesso
) {
    public static DocumentoFiscalResponseDTO from(DocumentoFiscal entidade) {
        return new DocumentoFiscalResponseDTO(
                entidade.getId(),
                entidade.getDocumento(),
                entidade.getSerie(),
                entidade.getNumero(),
                entidade.getStatus(),
                entidade.getChaveAcesso());
    }
}
