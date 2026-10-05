package com.l.erp.emissaofiscalservice.api.controllers;

import com.l.erp.emissaofiscalservice.api.dto.ConfiguracaoNfseRequestDTO;
import com.l.erp.emissaofiscalservice.api.dto.ConfiguracaoNfseResponseDTO;
import com.l.erp.emissaofiscalservice.services.nfse.ConfiguracaoNfseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Configuração de NFS-e por emitente (município + provedor) — spec §4.4. {@code emitenteId} é o
 * identificador neutro de quem chama. Sem ela, {@code POST /emissao/documentos/nfse} responde 422.
 */
@RestController
public class ConfiguracaoNfseController {

    private final ConfiguracaoNfseService service;

    public ConfiguracaoNfseController(ConfiguracaoNfseService service) {
        this.service = service;
    }

    @PutMapping("/emissao/configuracoes-nfse/{emitenteId}")
    public ConfiguracaoNfseResponseDTO salvar(@PathVariable UUID emitenteId,
                                               @Valid @RequestBody ConfiguracaoNfseRequestDTO request) {
        return ConfiguracaoNfseResponseDTO.from(service.salvar(emitenteId, request));
    }

    @GetMapping("/emissao/configuracoes-nfse/{emitenteId}")
    public ConfiguracaoNfseResponseDTO buscar(@PathVariable UUID emitenteId) {
        return ConfiguracaoNfseResponseDTO.from(service.buscar(emitenteId));
    }
}
