package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.ProdutoCategoriaDTO;
import com.l.erp.cadastroservice.api.dto.ProdutoCategoriaResponseDTO;
import com.l.erp.cadastroservice.api.mappers.ProdutoCategoriaAssembler;
import com.l.erp.cadastroservice.domain.ProdutoCategoria;
import com.l.erp.cadastroservice.services.ProdutoCategoriaService;
import com.l.erp.cadastroservice.util.Constants;
import com.l.erp.cadastroservice.util.SecurityUtils;
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

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/produtos/categorias")
public class ProdutoCategoriaController {
    private final Logger logger = LoggerFactory.getLogger(ProdutoCategoriaController.class);
    private final ProdutoCategoriaService service;
    private final ProdutoCategoriaAssembler assembler;

    public ProdutoCategoriaController(ProdutoCategoriaService service, ProdutoCategoriaAssembler assembler) {
        this.service = service;
        this.assembler = assembler;
    }

    @GetMapping
    public ResponseEntity<PagedModel<ProdutoCategoriaResponseDTO>> findAll(Pageable pageable, PagedResourcesAssembler<ProdutoCategoria> pagedResourcesAssembler) {
        logger.info("Listando Categoria de Produtos");
        Optional<Long> tenantId = SecurityUtils.getCurrentTenantId();

        Page<ProdutoCategoria> vendedoresPage = service.getAllProdCategorias(
                tenantId.orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND)), pageable);


        return ResponseEntity.ok(pagedResourcesAssembler.toModel(vendedoresPage, assembler));
    }

    @GetMapping("/ativas")
    public ResponseEntity<PagedModel<ProdutoCategoriaResponseDTO>> findAllEnabled(Pageable pageable, PagedResourcesAssembler<ProdutoCategoria> pagedResourcesAssembler) {
        logger.info("Listando Categoria de Produtos Ativas");
        Optional<Long> tenantId = SecurityUtils.getCurrentTenantId();

        Page<ProdutoCategoria> vendedoresPage = service.getAllProdCategoriasEnabled(
                tenantId.orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND)), pageable);


        return ResponseEntity.ok(pagedResourcesAssembler.toModel(vendedoresPage, assembler));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProdutoCategoriaResponseDTO> findById(@PathVariable UUID id) {
        logger.info("Buscando Categoria de Produto por ID: {}", id);
        Optional<Long> tenantId = SecurityUtils.getCurrentTenantId();

        ProdutoCategoria dto = service.findById(id, tenantId.orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND)));

        return ResponseEntity.ok(assembler.toModel(dto));
    }

    @PostMapping
    public ResponseEntity<ProdutoCategoriaResponseDTO> save(@RequestBody @Valid ProdutoCategoriaDTO dto) {
        logger.info("Criando Categoria de Produto: {}", dto);
        Long tenantId = SecurityUtils.getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND));
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException("User Id não encontrado!"));

        ProdutoCategoria salvo = service.save(dto, tenantId, userId);
        ProdutoCategoriaResponseDTO response = assembler.toModel(salvo);

        return ResponseEntity.created(response.getRequiredLink(org.springframework.hateoas.IanaLinkRelations.SELF).toUri())
                .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProdutoCategoriaResponseDTO> update(@PathVariable UUID id, @RequestBody @Valid ProdutoCategoriaDTO dto) {
        logger.info("Atualizando Categoria de Produto: {}", dto);
        Long tenantId = SecurityUtils.getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND));
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException("User Id não encontrado!"));

        ProdutoCategoria salvo = service.update(id, dto, tenantId, userId);
        ProdutoCategoriaResponseDTO response = assembler.toModel(salvo);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id) {
        logger.info("Atualizando status da Categoria do Produto ID: {}", id);
        Long tenantId = SecurityUtils.getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND));
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND));

        service.updateStatus(id, tenantId, userId);

        return ResponseEntity.noContent().build();
    }
}
