package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.CondicaoPagamentoParcelaRequestDTO;
import com.l.erp.cadastroservice.api.dto.CondicaoPagamentoParcelaResponseDTO;
import com.l.erp.cadastroservice.api.mappers.CondicaoPagamentoParcelaAssembler;
import com.l.erp.cadastroservice.domain.CondicaoPagamentoParcela;
import com.l.erp.cadastroservice.services.CondicaoPagamentoParcelaService;
import com.l.erp.cadastroservice.util.Constants;
import com.l.erp.cadastroservice.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cond-pagamentos/{condicaoPagamentoId}/parcelas")
public class CondicaoPagamentoParcelaController {

    private final Logger logger = LoggerFactory.getLogger(CondicaoPagamentoParcelaController.class);
    private final CondicaoPagamentoParcelaService service;
    private final CondicaoPagamentoParcelaAssembler assembler;

    public CondicaoPagamentoParcelaController(CondicaoPagamentoParcelaService service, CondicaoPagamentoParcelaAssembler assembler) {
        this.service = service;
        this.assembler = assembler;
    }

    @GetMapping
    public ResponseEntity<CollectionModel<CondicaoPagamentoParcelaResponseDTO>> getParcelasByCondicaoId(@PathVariable UUID condicaoPagamentoId) {
        logger.info("Listando parcelas para a condição ID: {}", condicaoPagamentoId);
        Long tenantId = SecurityUtils.getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.USUARIO_UUID_NAO_ENCONTRADO));

        List<CondicaoPagamentoParcela> parcelas = service.findByCondicaoPagamentoId(condicaoPagamentoId, tenantId);
        return ResponseEntity.ok(assembler.toCollectionModel(parcelas));
    }

    @PutMapping // Usando PUT em lote para substituir todas as parcelas de uma vez
    public ResponseEntity<CollectionModel<CondicaoPagamentoParcelaResponseDTO>> saveParcelas(
            @PathVariable UUID condicaoPagamentoId,
            @RequestBody @Valid List<CondicaoPagamentoParcelaRequestDTO> dtos) {

        logger.info("Atualizando parcelas para a condição ID: {}", condicaoPagamentoId);
        Long tenantId = SecurityUtils.getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.USUARIO_UUID_NAO_ENCONTRADO));
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USUARIO_UUID_NAO_ENCONTRADO));

        List<CondicaoPagamentoParcela> saved = service.saveAll(condicaoPagamentoId, dtos, tenantId, userId);
        return ResponseEntity.ok(assembler.toCollectionModel(saved));
    }

}
