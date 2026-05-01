package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.ContatoRequestDTO;
import com.l.erp.cadastroservice.api.dto.ContatoResponseDTO;
import com.l.erp.cadastroservice.api.mappers.ContatoAssembler;
import com.l.erp.cadastroservice.domain.Contato;
import com.l.erp.cadastroservice.services.ContatoService;
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
@RequestMapping("/api/v1/pessoas/{pessoaId}/contatos")
public class ContatoController {

    private final ContatoService contatoService;
    private final ContatoAssembler assembler;

    public ContatoController(ContatoService contatoService, ContatoAssembler assembler) {
        this.contatoService = contatoService;
        this.assembler = assembler;
    }

    @GetMapping
    public ResponseEntity<CollectionModel<ContatoResponseDTO>> listarPorPessoa(@PathVariable UUID pessoaId) {
        Long tenantId = getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND));

        List<Contato> contatos = contatoService.findAllByPessoa(pessoaId, tenantId);
        CollectionModel<ContatoResponseDTO> response = assembler.toCollectionModel(contatos);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContatoResponseDTO> findById(@PathVariable UUID pessoaId, @PathVariable UUID id) {
        Long tenantId = getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND));

        Contato contato = contatoService.findById(id, pessoaId, tenantId);
        return ResponseEntity.ok(assembler.toModel(contato));
    }

    @PostMapping
    public ResponseEntity<ContatoResponseDTO> criarParaPessoa(
            @PathVariable UUID pessoaId,
            @Valid @RequestBody ContatoRequestDTO dto) {

        Long tenantId = getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND));
        UUID userId = getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND));

        Contato salvo = contatoService.create(pessoaId, dto, tenantId, userId);
        ContatoResponseDTO response = assembler.toModel(salvo);

        return ResponseEntity
                .created(response.getRequiredLink(org.springframework.hateoas.IanaLinkRelations.SELF).toUri())
                .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContatoResponseDTO> atualizar(
            @PathVariable UUID pessoaId,
            @PathVariable UUID id,
            @Valid @RequestBody ContatoRequestDTO dto) {

        Long tenantId = getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.TENANT_NOT_FOUND));
        UUID userId = getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND));

        Contato atualizado = contatoService.update(id, pessoaId, dto, tenantId, userId);
        return ResponseEntity.ok(assembler.toModel(atualizado));
    }
}
