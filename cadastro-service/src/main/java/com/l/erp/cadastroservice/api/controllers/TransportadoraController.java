package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.TransportadoraDTO;
import com.l.erp.cadastroservice.api.dto.TransportadoraResponseDTO;
import com.l.erp.cadastroservice.api.mappers.TransportadoraAssembler;
import com.l.erp.cadastroservice.domain.Transportadora;
import com.l.erp.cadastroservice.services.TransportadoraService;
import com.l.erp.cadastroservice.util.SecurityUtils;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transportadoras")
public class TransportadoraController {
    private final Logger logger = LoggerFactory.getLogger(TransportadoraController.class);
    private final TransportadoraService service;
    private final TransportadoraAssembler assembler;

    public TransportadoraController(TransportadoraService service, TransportadoraAssembler assembler) {
        this.service = service;
        this.assembler = assembler;
    }

    @GetMapping
    public ResponseEntity<PagedModel<TransportadoraResponseDTO>> findAll(Pageable pageable, PagedResourcesAssembler<Transportadora> pagedResourcesAssembler) {
        logger.info("Listando Transportadoras");
        Page<Transportadora> transportadorasPage = service.getAllTransportadoras(pageable);
        return ResponseEntity.ok(pagedResourcesAssembler.toModel(transportadorasPage, assembler));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransportadoraResponseDTO> findById(@PathVariable UUID id) {
        logger.info("Buscando Transportadora por ID: {}", id);
        Transportadora transportadora = service.findById(id);
        return ResponseEntity.ok(assembler.toModel(transportadora));
    }

    @PostMapping
    public ResponseEntity<TransportadoraResponseDTO> save(@RequestBody @Valid TransportadoraDTO dto) {
        logger.info("Criando Transportadora: {}", dto);
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException("User Id não encontrado!"));

        Transportadora salvo = service.save(dto, userId);
        TransportadoraResponseDTO response = assembler.toModel(salvo);

        return ResponseEntity.created(response.getRequiredLink(org.springframework.hateoas.IanaLinkRelations.SELF).toUri())
                .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransportadoraResponseDTO> update(@PathVariable UUID id, @RequestBody @Valid TransportadoraDTO dto) {
        logger.info("Atualizando Transportadora: {}", dto);
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException("User Id não encontrado!"));

        Transportadora salvo = service.update(id, dto, userId);
        TransportadoraResponseDTO response = assembler.toModel(salvo);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id) {
        logger.info("Alterando status da Transportadora por ID: {}", id);
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException("User Id não encontrado!"));

        service.updateStatus(id, userId);

        return ResponseEntity.noContent().build();
    }
}