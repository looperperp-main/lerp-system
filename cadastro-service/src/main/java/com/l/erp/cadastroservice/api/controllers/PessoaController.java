package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.PessoaRequestDTO;
import com.l.erp.cadastroservice.api.dto.PessoaResponseDTO;
import com.l.erp.cadastroservice.api.mappers.PessoaAssembler;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.services.PessoaService;
import com.l.erp.cadastroservice.util.Constants;
import jakarta.validation.Valid;
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

import static com.l.erp.cadastroservice.util.SecurityUtils.getCurrentTenantId;
import static com.l.erp.cadastroservice.util.SecurityUtils.getCurrentUserId;

@RestController
@RequestMapping("/api/v1/pessoas")
public class PessoaController {

    private final PessoaService pessoaService;
    private final PessoaAssembler assembler;

    public PessoaController(PessoaService pessoaService, PessoaAssembler assembler) {
        this.pessoaService = pessoaService;
        this.assembler = assembler;
    }

    /**
     * Retrieves a paginated list of all Pessoa entities associated with the current tenant.
     *
     * @param pageable the pagination information including page number and size
     * @param pagedResourcesAssembler the assembler to convert Pessoa entities into a paginated model
     * @return a ResponseEntity containing a PagedModel of PessoaResponseDTO representing the paginated result
     * @throws RuntimeException if the tenant ID is not found
     */
    @GetMapping
    public ResponseEntity<PagedModel<PessoaResponseDTO>> findAll(Pageable pageable, PagedResourcesAssembler<Pessoa> pagedResourcesAssembler) {
        Optional<Long> tenantId = getCurrentTenantId();
        Page<Pessoa> pessoas = pessoaService.findAllByTenant(tenantId.orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND)), pageable);
        return ResponseEntity.ok(pagedResourcesAssembler.toModel(pessoas, assembler));
    }

    /**
     * Retrieves a Pessoa entity by its unique identifier (UUID) for the current tenant.
     *
     * @param id the unique identifier of the Pessoa entity to retrieve
     * @return a ResponseEntity containing a PessoaResponseDTO representation of the Pessoa entity
     * @throws RuntimeException if the tenant ID is not found or if the Pessoa entity for the given ID does not exist
     */
    @GetMapping("/{id}")
    public ResponseEntity<PessoaResponseDTO> findById(@PathVariable UUID id) {
        Optional<Long> tenantId = getCurrentTenantId();
        Pessoa pessoa = pessoaService.findByIdAndTenant(id, tenantId.orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND)));
        return ResponseEntity.ok(assembler.toModel(pessoa));
    }

    /**
     * Creates a new Pessoa entity for the current tenant and user.
     *
     * @param request the PessoaRequestDTO object containing the data to create a new Pessoa entity
     * @return a ResponseEntity containing a PessoaResponseDTO representation of the newly created Pessoa entity
     * @throws RuntimeException if the tenant ID or user ID is not found
     */
    @PostMapping
    public ResponseEntity<PessoaResponseDTO> create(@Valid @RequestBody PessoaRequestDTO request) {
        Optional<Long> tenantId = getCurrentTenantId();
        Optional<UUID> userId = getCurrentUserId();

        Pessoa salva = pessoaService.create(request, tenantId.orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND)), userId.orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND)));
        PessoaResponseDTO response = assembler.toModel(salva);

        return ResponseEntity
                .created(response.getRequiredLink(org.springframework.hateoas.IanaLinkRelations.SELF).toUri())
                .body(response);
    }

    /**
     * Updates an existing Pessoa entity for the current tenant and user.
     *
     * @param id the unique identifier of the Pessoa entity to update
     * @param dto the PessoaRequestDTO object containing updated data for the Pessoa entity
     * @return a ResponseEntity containing a PessoaResponseDTO representation of the updated Pessoa entity
     * @throws RuntimeException if the tenant ID or user ID is not found
     */
    @PutMapping("/{id}")
    public ResponseEntity<PessoaResponseDTO> upadte(@PathVariable UUID id, @RequestBody @Valid PessoaRequestDTO dto) {
        Optional<Long> tenantId = getCurrentTenantId();
        Optional<UUID> userId = getCurrentUserId();

        Pessoa salva = pessoaService.update(id, dto, tenantId.orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND)), userId.orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND)));
        PessoaResponseDTO response = assembler.toModel(salva);

        return ResponseEntity
                .created(response.getRequiredLink(org.springframework.hateoas.IanaLinkRelations.SELF).toUri())
                .body(response);
    }
}
