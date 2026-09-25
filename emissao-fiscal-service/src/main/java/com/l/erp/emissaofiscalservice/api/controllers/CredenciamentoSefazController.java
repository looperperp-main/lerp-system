package com.l.erp.emissaofiscalservice.api.controllers;

import com.l.erp.emissaofiscalservice.api.dto.CredenciamentoSefazRequestDTO;
import com.l.erp.emissaofiscalservice.api.dto.CredenciamentoSefazResponseDTO;
import com.l.erp.emissaofiscalservice.services.credenciamento.CredenciamentoSefazService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Checklist de credenciamento fiscal por emitente — spec §3 item 11. {@code emitenteId} é um
 * identificador neutro fornecido por quem chama (spec §2, "vendável separadamente").
 */
@RestController
public class CredenciamentoSefazController {

    private final CredenciamentoSefazService service;

    public CredenciamentoSefazController(CredenciamentoSefazService service) {
        this.service = service;
    }

    @PostMapping("/emissao/credenciamentos/{emitenteId}")
    public ResponseEntity<CredenciamentoSefazResponseDTO> registrar(@PathVariable UUID emitenteId,
                                                                      @Valid @RequestBody CredenciamentoSefazRequestDTO request) {
        var credenciamento = service.registrar(emitenteId, request.uf().toUpperCase(), request.modelo(), request.status());
        return ResponseEntity.status(HttpStatus.CREATED).body(CredenciamentoSefazResponseDTO.from(credenciamento));
    }

    @GetMapping("/emissao/credenciamentos/{emitenteId}")
    public ResponseEntity<List<CredenciamentoSefazResponseDTO>> listar(@PathVariable UUID emitenteId) {
        var credenciamentos = service.listarPorEmitente(emitenteId).stream()
                .map(CredenciamentoSefazResponseDTO::from)
                .toList();
        return ResponseEntity.ok(credenciamentos);
    }
}
