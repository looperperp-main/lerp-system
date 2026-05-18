package com.l.erp.cadastroservice.services;

import com.l.erp.cadastroservice.api.dto.ViaCepResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class CepService {

    @Value("${cep.service.url}")
    private String cepServiceURL;

    public ViaCepResponseDTO buscarCep(String cep) {
        RestTemplate restTemplate = new RestTemplate();
        // Remove caracteres não numéricos do CEP
        String cepFormatado = cep.replaceAll("[\\D]", "");
        String url = cepServiceURL + cepFormatado + "/json/";

        return restTemplate.getForObject(url, ViaCepResponseDTO.class);
    }
}
