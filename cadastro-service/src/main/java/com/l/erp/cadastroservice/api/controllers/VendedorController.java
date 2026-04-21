package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.VendedorDTO;
import com.l.erp.cadastroservice.api.dto.VendedorResponseDTO;
import com.l.erp.cadastroservice.api.mappers.VendedorAssembler;
import com.l.erp.cadastroservice.domain.Vendedor;
import com.l.erp.cadastroservice.services.VendedorService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vendedores")
public class VendedorController {
    private final Logger logger = LoggerFactory.getLogger(VendedorController.class);
    private final VendedorService service;
    private final VendedorAssembler assembler;

    public VendedorController(VendedorService service, VendedorAssembler assembler) {
        this.service = service;
        this.assembler = assembler;
    }

    /**
     * Retrieves a paginated list of vendedores from the system.
     *
     * @param pageable the pagination information specifying the page size, number, and sorting order
     * @param pagedResourcesAssembler the assembler to convert the Page of Vendedor entities into a PagedModel
     * @return a ResponseEntity containing a PagedModel of VendedorResponseDTO with the paginated list of vendedores
     */
    @GetMapping
    public ResponseEntity<PagedModel<VendedorResponseDTO>> findAll(Pageable pageable, PagedResourcesAssembler<Vendedor> pagedResourcesAssembler) {
        logger.info("Listando Vendedores");
        Optional<Long> tenantId = SecurityUtils.getCurrentTenantId();

        Page<Vendedor> vendedoresPage = service.getAllVendedores(
                tenantId.orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND)), pageable);


        return ResponseEntity.ok(pagedResourcesAssembler.toModel(vendedoresPage, assembler));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VendedorResponseDTO> findById(@PathVariable UUID id) {
        logger.info("Buscando Vendedor por ID: {}", id);
        Optional<Long> tenantId = SecurityUtils.getCurrentTenantId();

        Vendedor dto = service.findById(id, tenantId.orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND)));

        return ResponseEntity.ok(assembler.toModel(dto));
    }

    @PostMapping
    public ResponseEntity<VendedorResponseDTO> save(@RequestBody @Valid VendedorDTO dto) {
        logger.info("Criando Vendedor: {}", dto);
        Long tenantId = SecurityUtils.getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND));
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException("User Id não encontrado!"));

        Vendedor salvo = service.save(dto, tenantId, userId);
        VendedorResponseDTO response = assembler.toModel(salvo);

        return ResponseEntity.created(response.getRequiredLink(org.springframework.hateoas.IanaLinkRelations.SELF).toUri())
                .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<VendedorResponseDTO> update(@PathVariable UUID id, @RequestBody @Valid VendedorDTO dto) {
        logger.info("Atualizando Vendedor: {}", dto);
        Long tenantId = SecurityUtils.getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND));
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException("User Id não encontrado!"));

        Vendedor salvo = service.update(id, dto, tenantId, userId);
        VendedorResponseDTO response = assembler.toModel(salvo);

        return ResponseEntity.ok(response);
    }
}
