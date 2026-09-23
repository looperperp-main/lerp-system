package com.l.erp.emissaofiscalservice.api.controllers;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.emissaofiscalservice.api.dto.DocumentoFiscalRequestDTO;
import com.l.erp.emissaofiscalservice.api.dto.DocumentoFiscalResponseDTO;
import com.l.erp.emissaofiscalservice.services.documento.DocumentoFiscalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DocumentoFiscalController {

    private final DocumentoFiscalService service;

    public DocumentoFiscalController(DocumentoFiscalService service) {
        this.service = service;
    }

    /**
     * Cria o documento em RASCUNHO — spec §3 item 10. Responde 201 (não 202: nesta etapa não há
     * assinatura/transmissão real ainda, só o registro do rascunho + reserva de número; a resposta
     * 202 com desfecho assíncrono passa a valer a partir da Etapa 2, quando a transmissão à SEFAZ
     * existir de fato). {@code Idempotency-Key} é obrigatório e validado manualmente (não via
     * {@code required=true} do Spring) para poder devolver 400 em PT-BR consistente com o
     * GlobalExceptionHandler, em vez do 400 genérico do Spring pra header ausente.
     */
    @PostMapping("/emissao/documentos")
    public ResponseEntity<DocumentoFiscalResponseDTO> criar(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                              @Valid @RequestBody DocumentoFiscalRequestDTO request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException("Header Idempotency-Key é obrigatório.", HttpStatus.BAD_REQUEST);
        }
        var documento = service.criar(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentoFiscalResponseDTO.from(documento));
    }
}
