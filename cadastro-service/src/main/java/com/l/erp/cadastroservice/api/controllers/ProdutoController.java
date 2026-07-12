package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.ProdutoDTO;
import com.l.erp.cadastroservice.api.dto.ProdutoResponseDTO;
import com.l.erp.cadastroservice.api.mappers.ProdutoAssembler;
import com.l.erp.cadastroservice.domain.Produto;
import com.l.erp.cadastroservice.services.ProdutoService;
import com.l.erp.cadastroservice.util.SecurityUtils;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/produtos")
public class ProdutoController {

    private final ProdutoService service;
    private final ProdutoAssembler assembler;
    private final Logger logger = LoggerFactory.getLogger(ProdutoController.class);


    public ProdutoController(ProdutoService service, ProdutoAssembler assembler) {
        this.service = service;
        this.assembler = assembler;
    }

    @GetMapping
    public ResponseEntity<PagedModel<ProdutoResponseDTO>> findAll(Pageable pageable, PagedResourcesAssembler<Produto> pagedResourcesAssembler) {
        Optional<Long> tenantId = SecurityUtils.getCurrentTenantId();
        Page<Produto> produtos = service.findAll(tenantId.orElseThrow(() -> new BusinessException(Constants.TENANT_NOT_FOUND, HttpStatus.UNAUTHORIZED)), pageable);
        return ResponseEntity.ok(pagedResourcesAssembler.toModel(produtos, assembler));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProdutoResponseDTO> findById(@PathVariable UUID id) {
        Long tenantId = SecurityUtils.getCurrentTenantId().orElseThrow(() -> new BusinessException(Constants.TENANT_NOT_FOUND, HttpStatus.UNAUTHORIZED));
        Produto produto = service.findById(id, tenantId);
        return ResponseEntity.ok(assembler.toModel(produto));
    }

    @PostMapping
    public ResponseEntity<ProdutoResponseDTO> create(@Valid @RequestBody ProdutoDTO dto) {
        Long tenantId = SecurityUtils.getCurrentTenantId().orElseThrow(() -> new BusinessException(Constants.TENANT_NOT_FOUND, HttpStatus.UNAUTHORIZED));
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException("User Id não encontrado!"));

        Produto created = service.create(tenantId, userId, dto);
        ProdutoResponseDTO responseModel = assembler.toModel(created);

        URI uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(uri).body(responseModel);
    }

    /**
     * Updates the status of a given entity identified by its ID.
     *
     * @param id the unique identifier of the entity whose status is being updated
     * @return a {@code ResponseEntity} with no content (HTTP status 204) indicating the update operation was successful
     * @throws BusinessException if the tenant or user ID cannot be resolved
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id){
        logger.info("Atualizando status da Pessoa ID: {}", id);
        Long tenantId = SecurityUtils.getCurrentTenantId().orElseThrow(() -> new BusinessException(Constants.TENANT_NOT_FOUND, HttpStatus.UNAUTHORIZED));
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new BusinessException(Constants.USER_NOT_FOUND, HttpStatus.UNAUTHORIZED));

        service.updateStatus(id, tenantId, userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProdutoResponseDTO> update(@PathVariable UUID id, @Valid @RequestBody ProdutoDTO dto) {
        Optional<Long> tenantId = SecurityUtils.getCurrentTenantId();
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException("User Id não encontrado!"));
        Produto updated = service.update(id, userId, dto,tenantId.orElseThrow(() -> new BusinessException(Constants.TENANT_NOT_FOUND, HttpStatus.UNAUTHORIZED)));
        return ResponseEntity.ok(assembler.toModel(updated));
    }

    // Mocks para os links HATEOAS
    @GetMapping("/{id}/precos")
    public ResponseEntity<Void> findPrecos(@PathVariable UUID id) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/fornecedores")
    public ResponseEntity<Void> findFornecedores(@PathVariable UUID id) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/estoque-config")
    public ResponseEntity<Void> findEstoqueConfig(@PathVariable UUID id) {
        return ResponseEntity.ok().build();
    }
}
