package com.l.erp.emissaofiscalservice.api.dto;

import com.l.erp.emissaofiscalservice.domain.CodigoProvedorNfse;
import com.l.erp.emissaofiscalservice.domain.ConfiguracaoNfse;

import java.util.UUID;

public record ConfiguracaoNfseResponseDTO(UUID emitenteId, String municipioIbge, CodigoProvedorNfse provedor,
                                          String inscricaoMunicipal) {

    public static ConfiguracaoNfseResponseDTO from(ConfiguracaoNfse configuracao) {
        return new ConfiguracaoNfseResponseDTO(configuracao.getEmitenteId(), configuracao.getMunicipioIbge(),
                configuracao.getProvedor(), configuracao.getInscricaoMunicipal());
    }
}
