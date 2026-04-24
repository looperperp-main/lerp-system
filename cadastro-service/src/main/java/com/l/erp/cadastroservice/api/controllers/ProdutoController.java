package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.ProdutoDTO;
import com.l.erp.cadastroservice.api.dto.ProdutoRequestDTO;
import com.l.erp.cadastroservice.api.dto.ProdutoResponseDTO;
import com.l.erp.cadastroservice.api.mappers.ProdutoAssembler;
import com.l.erp.cadastroservice.domain.Produto;
import com.l.erp.cadastroservice.domain.Vendedor;
import com.l.erp.cadastroservice.services.ProdutoService;
import com.l.erp.cadastroservice.util.Constants;
import com.l.erp.cadastroservice.util.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

    public ProdutoController(ProdutoService service, ProdutoAssembler assembler) {
        this.service = service;
        this.assembler = assembler;
    }

    @GetMapping
    public ResponseEntity<PagedModel<ProdutoResponseDTO>> findAll(Pageable pageable, PagedResourcesAssembler<Produto> pagedResourcesAssembler) {
        Optional<Long> tenantId = SecurityUtils.getCurrentTenantId();
        Page<Produto> produtos = service.findAll(tenantId.orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND)), pageable);
        return ResponseEntity.ok(pagedResourcesAssembler.toModel(produtos, assembler));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProdutoResponseDTO> findById(@PathVariable UUID id) {
        Produto produto = service.findById(id);
        return ResponseEntity.ok(assembler.toModel(produto));
    }

    @PostMapping
    public ResponseEntity<ProdutoResponseDTO> create(@Valid @RequestBody ProdutoDTO dto) {
        Long tenantId = 1L; // mock
        UUID userId = UUID.randomUUID(); // mock

        Produto created = service.create(tenantId, userId, dto);
        ProdutoResponseDTO responseModel = assembler.toModel(created);

        URI uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(uri).body(responseModel);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProdutoResponseDTO> update(@PathVariable UUID id, @Valid @RequestBody ProdutoDTO dto) {
        Optional<Long> tenantId = SecurityUtils.getCurrentTenantId();
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException("User Id não encontrado!"));
        Produto updated = service.update(id, userId, dto,tenantId.orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND)));
        return ResponseEntity.ok(assembler.toModel(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException("User Id não encontrado!"));
        service.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    // Mocks para os links HATEOAS
    @GetMapping("/{id}/precos")
    public ResponseEntity<?> findPrecos(@PathVariable UUID id) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/fornecedores")
    public ResponseEntity<?> findFornecedores(@PathVariable UUID id) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/estoque-config")
    public ResponseEntity<?> findEstoqueConfig(@PathVariable UUID id) {
        return ResponseEntity.ok().build();
    }
}
