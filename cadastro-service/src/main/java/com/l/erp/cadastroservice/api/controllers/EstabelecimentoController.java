package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.EstabelecimentoRequestDTO;
import com.l.erp.cadastroservice.api.dto.EstabelecimentoResponseDTO;
import com.l.erp.cadastroservice.api.mappers.EstabelecimentoAssembler;
import com.l.erp.cadastroservice.domain.Estabelecimento;
import com.l.erp.cadastroservice.services.EstabelecimentoService;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import jakarta.validation.Valid;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

/**
 * CRUD de filiais de uma Pessoa PJ. A matriz (ordem 0001) é criada/atualizada
 * automaticamente por PessoaService — não aparece aqui como operação de escrita.
 */
@RestController
@RequestMapping("/api/v1/pessoas/{pessoaId}/estabelecimentos")
public class EstabelecimentoController {

    private final EstabelecimentoService estabelecimentoService;
    private final EstabelecimentoAssembler assembler;

    public EstabelecimentoController(EstabelecimentoService estabelecimentoService, EstabelecimentoAssembler assembler) {
        this.estabelecimentoService = estabelecimentoService;
        this.assembler = assembler;
    }

    @GetMapping
    public ResponseEntity<CollectionModel<EstabelecimentoResponseDTO>> listarPorPessoa(@PathVariable UUID pessoaId) {
        Long tenantId = getCurrentTenantId().orElseThrow(() -> new BusinessException(Constants.TENANT_NOT_FOUND, HttpStatus.UNAUTHORIZED));
        List<Estabelecimento> estabelecimentos = estabelecimentoService.findAllByPessoa(pessoaId, tenantId);
        CollectionModel<EstabelecimentoResponseDTO> response = assembler.toCollectionModel(estabelecimentos);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EstabelecimentoResponseDTO> findById(@PathVariable UUID pessoaId, @PathVariable UUID id) {
        Long tenantId = getCurrentTenantId().orElseThrow(() -> new BusinessException(Constants.TENANT_NOT_FOUND, HttpStatus.UNAUTHORIZED));
        Estabelecimento estabelecimento = estabelecimentoService.findById(id, pessoaId, tenantId);
        return ResponseEntity.ok(assembler.toModel(estabelecimento));
    }

    @PostMapping
    public ResponseEntity<EstabelecimentoResponseDTO> criarParaPessoa(
            @PathVariable UUID pessoaId,
            @Valid @RequestBody EstabelecimentoRequestDTO dto) {
        Long tenantId = getCurrentTenantId().orElseThrow(() -> new BusinessException(Constants.TENANT_NOT_FOUND, HttpStatus.UNAUTHORIZED));
        UUID userId = getCurrentUserId().orElseThrow(() -> new BusinessException(Constants.USER_NOT_FOUND, HttpStatus.UNAUTHORIZED));
        Estabelecimento salvo = estabelecimentoService.create(pessoaId, dto, tenantId, userId);
        EstabelecimentoResponseDTO response = assembler.toModel(salvo);
        return ResponseEntity
                .created(response.getRequiredLink(org.springframework.hateoas.IanaLinkRelations.SELF).toUri())
                .body(response);
    }

    /** Marca a matriz como o estabelecimento próprio do tenant — chamado pelo onboarding do auth-service. */
    @PatchMapping("/matriz/proprio")
    public ResponseEntity<EstabelecimentoResponseDTO> marcarProprio(@PathVariable UUID pessoaId) {
        Long tenantId = getCurrentTenantId().orElseThrow(() -> new BusinessException(Constants.TENANT_NOT_FOUND, HttpStatus.UNAUTHORIZED));
        UUID userId = getCurrentUserId().orElseThrow(() -> new BusinessException(Constants.USER_NOT_FOUND, HttpStatus.UNAUTHORIZED));
        Estabelecimento atualizado = estabelecimentoService.marcarProprio(pessoaId, tenantId, userId);
        return ResponseEntity.ok(assembler.toModel(atualizado));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EstabelecimentoResponseDTO> atualizar(
            @PathVariable UUID pessoaId,
            @PathVariable UUID id,
            @Valid @RequestBody EstabelecimentoRequestDTO dto) {
        Long tenantId = getCurrentTenantId().orElseThrow(() -> new BusinessException(Constants.TENANT_NOT_FOUND, HttpStatus.UNAUTHORIZED));
        UUID userId = getCurrentUserId().orElseThrow(() -> new BusinessException(Constants.USER_NOT_FOUND, HttpStatus.UNAUTHORIZED));
        Estabelecimento atualizado = estabelecimentoService.update(id, pessoaId, dto, tenantId, userId);
        return ResponseEntity.ok(assembler.toModel(atualizado));
    }
}
