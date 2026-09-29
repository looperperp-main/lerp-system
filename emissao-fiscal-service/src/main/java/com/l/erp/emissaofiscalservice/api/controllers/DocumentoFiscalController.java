package com.l.erp.emissaofiscalservice.api.controllers;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.emissaofiscalservice.api.dto.DocumentoFiscalRequestDTO;
import com.l.erp.emissaofiscalservice.api.dto.DocumentoFiscalResponseDTO;
import com.l.erp.emissaofiscalservice.services.documento.DocumentoFiscalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class DocumentoFiscalController {

    private final DocumentoFiscalService service;

    public DocumentoFiscalController(DocumentoFiscalService service) {
        this.service = service;
    }

    /**
     * Aceita o documento e responde 202 com o id — spec §3 item 10: o desfecho vem por evento Kafka e
     * por {@code GET /emissao/documentos/{id}}, nunca preso esperando a SEFAZ. O documento nasce em
     * RASCUNHO com número reservado; assinatura e transmissão rodam depois, de forma assíncrona.
     * {@code Idempotency-Key} é obrigatório e validado manualmente (não via {@code required=true} do
     * Spring) para devolver 400 em PT-BR consistente com o GlobalExceptionHandler.
     */
    @PostMapping(value = "/emissao/documentos", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DocumentoFiscalResponseDTO> criar(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                              @Valid @RequestBody DocumentoFiscalRequestDTO request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException("Header Idempotency-Key é obrigatório.", HttpStatus.BAD_REQUEST);
        }
        var documento = service.criar(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DocumentoFiscalResponseDTO.from(documento));
    }

    @GetMapping(value = "/emissao/documentos/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DocumentoFiscalResponseDTO buscar(@PathVariable UUID id) {
        return DocumentoFiscalResponseDTO.from(service.buscar(id));
    }
}
