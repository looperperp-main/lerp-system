package com.l.erp.emissaofiscalservice.api.controllers;

import com.l.erp.emissaofiscalservice.api.dto.CertificadoDigitalResponseDTO;
import com.l.erp.emissaofiscalservice.services.certificado.CertificadoDigitalService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
public class CertificadoDigitalController {

    private final CertificadoDigitalService service;

    public CertificadoDigitalController(CertificadoDigitalService service) {
        this.service = service;
    }

    /**
     * Upload/substituição do certificado A1 de um emitente — spec §3 item 1. {@code emitenteId} é
     * um identificador neutro fornecido por quem chama, nunca resolvido internamente (spec §2,
     * "vendável separadamente" — o contrato não pode depender do conceito de Estabelecimento do
     * erp-vsd). {@code cnpjEmitente} vem de quem chama (ex. operacoes-service, admin UI), nunca
     * buscado em cadastro-service (spec §2).
     */
    @PostMapping(value = "/emissao/certificados/{emitenteId}", consumes = "multipart/form-data")
    public ResponseEntity<CertificadoDigitalResponseDTO> upload(@PathVariable UUID emitenteId,
                                                                  @RequestParam String cnpjEmitente,
                                                                  @RequestParam String senha,
                                                                  @RequestPart("arquivo") MultipartFile arquivo) {
        var certificado = service.upload(emitenteId, cnpjEmitente, arquivo, senha);
        return ResponseEntity.status(HttpStatus.CREATED).body(CertificadoDigitalResponseDTO.from(certificado));
    }
}
