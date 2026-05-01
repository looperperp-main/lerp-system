package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.EnderecoRequestDTO;
import com.l.erp.cadastroservice.api.dto.EnderecoResponseDTO;
import com.l.erp.cadastroservice.api.mappers.EnderecoAssembler;
import com.l.erp.cadastroservice.domain.Endereco;
import com.l.erp.cadastroservice.services.EnderecoService;
import com.l.erp.common.util.Constants;
import jakarta.validation.Valid;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.l.erp.cadastroservice.util.SecurityUtils.getCurrentTenantId;
import static com.l.erp.cadastroservice.util.SecurityUtils.getCurrentUserId;

@RestController
@RequestMapping("/api/v1/pessoas/{pessoaId}/enderecos")
public class EnderecoController {

    private final EnderecoService enderecoService;
    private final EnderecoAssembler assembler;

    public EnderecoController(EnderecoService enderecoService, EnderecoAssembler assembler) {
        this.enderecoService = enderecoService;
        this.assembler = assembler;
    }

    @GetMapping
    public ResponseEntity<CollectionModel<EnderecoResponseDTO>> listarPorPessoa(@PathVariable UUID pessoaId) {
        Long tenantId = getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND));

        List<Endereco> enderecos = enderecoService.findAllByPessoa(pessoaId, tenantId);
        CollectionModel<EnderecoResponseDTO> response = assembler.toCollectionModel(enderecos);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnderecoResponseDTO> findById(@PathVariable UUID pessoaId, @PathVariable UUID id) {
        Long tenantId = getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND));

        Endereco endereco = enderecoService.findById(id, pessoaId, tenantId);
        return ResponseEntity.ok(assembler.toModel(endereco));
    }

    @PostMapping
    public ResponseEntity<EnderecoResponseDTO> criarParaPessoa(
            @PathVariable UUID pessoaId,
            @Valid @RequestBody EnderecoRequestDTO dto) {

        Long tenantId = getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND));
        UUID userId = getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND));

        Endereco salvo = enderecoService.create(pessoaId, dto, tenantId, userId);
        EnderecoResponseDTO response = assembler.toModel(salvo);

        return ResponseEntity
                .created(response.getRequiredLink(org.springframework.hateoas.IanaLinkRelations.SELF).toUri())
                .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EnderecoResponseDTO> atualizar(
            @PathVariable UUID pessoaId,
            @PathVariable UUID id,
            @Valid @RequestBody EnderecoRequestDTO dto) {

        Long tenantId = getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND));
        UUID userId = getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND));

        Endereco atualizado = enderecoService.update(id, pessoaId, dto, tenantId, userId);
        return ResponseEntity.ok(assembler.toModel(atualizado));
    }
}
