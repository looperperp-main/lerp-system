package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.FornecedorDto;
import com.l.erp.cadastroservice.api.dto.FornecedorResponseDTO;
import com.l.erp.cadastroservice.api.mappers.FornecedorAssembler;
import com.l.erp.cadastroservice.domain.Fornecedor;
import com.l.erp.cadastroservice.services.FornecedorService;
import com.l.erp.cadastroservice.util.SecurityUtils;
import com.l.erp.common.util.Constants;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fornecedores")
public class FornecedorController {
    private final Logger logger = LoggerFactory.getLogger(FornecedorController.class);
    private final FornecedorService service;
    private final FornecedorAssembler assembler;

    public FornecedorController(FornecedorService service, FornecedorAssembler assembler) {
        this.service = service;
        this.assembler = assembler;
    }

    @GetMapping
    public ResponseEntity<PagedModel<FornecedorResponseDTO>> findAll(Pageable pageable, PagedResourcesAssembler<Fornecedor> pagedResourcesAssembler) {
        logger.info("Listando Fornecedores");
        Page<Fornecedor> fornecedoresPage = service.getAllFornecedores(pageable);
        return ResponseEntity.ok(pagedResourcesAssembler.toModel(fornecedoresPage, assembler));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FornecedorResponseDTO> findById(@PathVariable UUID id) {
        logger.info("Buscando Fornecedor por ID: {}", id);
        Fornecedor fornecedor = service.findById(id);
        return ResponseEntity.ok(assembler.toModel(fornecedor));
    }

    @PostMapping
    public ResponseEntity<FornecedorResponseDTO> save(@RequestBody @Valid FornecedorDto dto) {
        logger.info("Criando Fornecedor: {}", dto);
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND));

        Fornecedor salvo = service.save(dto, userId);
        FornecedorResponseDTO response = assembler.toModel(salvo);

        return ResponseEntity.created(response.getRequiredLink(org.springframework.hateoas.IanaLinkRelations.SELF).toUri())
                .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FornecedorResponseDTO> update(@PathVariable UUID id, @RequestBody @Valid FornecedorDto dto) {
        logger.info("Atualizando Fornecedor: {}", dto);
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND));

        Fornecedor salvo = service.update(id, dto, userId);
        FornecedorResponseDTO response = assembler.toModel(salvo);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id) {
        logger.info("Alterando status do Fornecedor por ID: {}", id);
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND));

        service.updateStatus(id, userId);

        return ResponseEntity.noContent().build();
    }
}
