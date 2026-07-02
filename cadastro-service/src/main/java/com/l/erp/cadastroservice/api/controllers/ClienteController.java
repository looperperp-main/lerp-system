package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.ClienteDTO;
import com.l.erp.cadastroservice.api.dto.ClienteResponseDTO;
import com.l.erp.cadastroservice.api.mappers.ClienteAssembler;
import com.l.erp.cadastroservice.domain.Cliente;
import com.l.erp.cadastroservice.services.ClienteService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/clientes")
public class ClienteController {

    private final Logger logger = LoggerFactory.getLogger(ClienteController.class);
    private final ClienteService service;
    private final ClienteAssembler assembler;

    public ClienteController(ClienteService service, ClienteAssembler assembler) {
        this.service = service;
        this.assembler = assembler;
    }

    /**
     * Retrieves a paginated list of clientes from the system.
     *
     * @param pageable the pagination information specifying the page size, number, and sorting order
     * @param pagedResourcesAssembler the assembler to convert the Page of Cliente entities into a PagedModel
     * @return a ResponseEntity containing a PagedModel of ClienteResponseDTO with the paginated list of clientes
     */
    @GetMapping
    public ResponseEntity<PagedModel<ClienteResponseDTO>> findAll(Pageable pageable, PagedResourcesAssembler<Cliente> pagedResourcesAssembler) {
        logger.info("Listando Clientes");
        Optional<Long> tenantId = SecurityUtils.getCurrentTenantId();

        Page<Cliente> clientesPage = service.getAllClientes(
                tenantId.orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND)), pageable);

        return ResponseEntity.ok(pagedResourcesAssembler.toModel(clientesPage, assembler));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponseDTO> findById(@PathVariable UUID id) {
        logger.info("Buscando Cliente por ID: {}", id);
        Optional<Long> tenantId = SecurityUtils.getCurrentTenantId();

        Cliente dto = service.findById(id, tenantId.orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND)));

        return ResponseEntity.ok(assembler.toModel(dto));
    }

    @PostMapping
    public ResponseEntity<ClienteResponseDTO> save(@RequestBody @Valid ClienteDTO dto) {
        logger.info("Criando Cliente vinculado a Pessoa ID: {}", dto.pessoaId());
        Long tenantId = SecurityUtils.getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND));
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND));

        Cliente salvo = service.save(dto, tenantId, userId);
        ClienteResponseDTO response = assembler.toModel(salvo);

        return ResponseEntity.created(response.getRequiredLink(org.springframework.hateoas.IanaLinkRelations.SELF).toUri())
                .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteResponseDTO> update(@PathVariable UUID id, @RequestBody @Valid ClienteDTO dto) {
        logger.info("Atualizando Cliente ID: {}", id);
        Long tenantId = SecurityUtils.getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND));
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND));

        Cliente salvo = service.update(id, dto, tenantId, userId);
        ClienteResponseDTO response = assembler.toModel(salvo);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id) {
        logger.info("Atualizando status do Cliente ID: {}", id);
        Long tenantId = SecurityUtils.getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND));
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND));

        service.updateStatus(id, tenantId, userId);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        logger.info("Deletando Cliente ID: {}", id);
        Long tenantId = SecurityUtils.getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND));
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND));

        service.delete(id, tenantId, userId);

        return ResponseEntity.noContent().build();
    }
}
