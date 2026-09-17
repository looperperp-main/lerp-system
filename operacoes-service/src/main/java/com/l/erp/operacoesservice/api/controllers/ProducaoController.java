package com.l.erp.operacoesservice.api.controllers;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.api.dto.ApontarProducaoRequestDTO;
import com.l.erp.operacoesservice.api.dto.FichaTecnicaRequestDTO;
import com.l.erp.operacoesservice.api.dto.FichaTecnicaResponseDTO;
import com.l.erp.operacoesservice.api.dto.OrdemProducaoRequestDTO;
import com.l.erp.operacoesservice.api.dto.OrdemProducaoResponseDTO;
import com.l.erp.operacoesservice.api.mappers.ProducaoMapper;
import com.l.erp.operacoesservice.domain.estoque.FichaTecnica;
import com.l.erp.operacoesservice.domain.estoque.OrdemProducao;
import com.l.erp.operacoesservice.services.estoque.ProducaoService;
import com.l.erp.operacoesservice.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Ficha técnica + ordem de produção (spec/modulos/estoque/estoque.md §12, D11, Fase 2). */
@RestController
@RequestMapping("/api/v1/producao")
@Tag(name = "Produção", description = "Ficha técnica e ordem de produção própria")
public class ProducaoController {

    private final ProducaoService service;
    private final ProducaoMapper mapper;

    public ProducaoController(ProducaoService service, ProducaoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Operation(summary = "Criar ficha técnica", description = "Desativa a ficha ativa anterior do mesmo produto acabado, se existir.")
    @PostMapping("/fichas-tecnicas")
    @PreAuthorize("hasAuthority('PRODUCAO_GERENCIAR')")
    public ResponseEntity<FichaTecnicaResponseDTO> criarFichaTecnica(@RequestBody @Valid FichaTecnicaRequestDTO dto) {
        List<ProducaoService.ItemFicha> itens = dto.itens().stream()
                .map(i -> new ProducaoService.ItemFicha(i.produtoComponenteId(), i.quantidade()))
                .toList();
        FichaTecnica ficha = service.criarFichaTecnica(tenantId(), userId(), dto.produtoAcabadoId(), itens);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(ficha));
    }

    @Operation(summary = "Listar fichas técnicas")
    @GetMapping("/fichas-tecnicas")
    @PreAuthorize("hasAuthority('PRODUCAO_VISUALIZAR')")
    public ResponseEntity<PagedModel<FichaTecnicaResponseDTO>> fichasTecnicas(
            Pageable pageable, PagedResourcesAssembler<FichaTecnica> pagedResourcesAssembler) {
        Page<FichaTecnica> page = service.buscarFichasTecnicas(tenantId(), pageable);
        return ResponseEntity.ok(pagedResourcesAssembler.toModel(page, this::toResponse));
    }

    private FichaTecnicaResponseDTO toResponse(FichaTecnica ficha) {
        FichaTecnicaResponseDTO dto = mapper.toFichaTecnicaResponseDto(ficha);
        dto.setItens(service.buscarItens(ficha.getId()).stream().map(mapper::toItemDto).toList());
        return dto;
    }

    @Operation(summary = "Criar ordem de produção", description = "Exige ficha técnica ativa pro produto acabado.")
    @PostMapping("/ordens")
    @PreAuthorize("hasAuthority('PRODUCAO_GERENCIAR')")
    public ResponseEntity<OrdemProducaoResponseDTO> criarOrdem(@RequestBody @Valid OrdemProducaoRequestDTO dto) {
        OrdemProducao ordem = service.criarOrdemProducao(tenantId(), userId(), dto.produtoAcabadoId(),
                dto.quantidadePlanejada(), dto.depositoId());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toOrdemResponseDto(ordem));
    }

    @Operation(summary = "Listar ordens de produção")
    @GetMapping("/ordens")
    @PreAuthorize("hasAuthority('PRODUCAO_VISUALIZAR')")
    public ResponseEntity<PagedModel<OrdemProducaoResponseDTO>> ordens(
            Pageable pageable, PagedResourcesAssembler<OrdemProducao> pagedResourcesAssembler) {
        Page<OrdemProducao> page = service.buscarOrdens(tenantId(), pageable);
        return ResponseEntity.ok(pagedResourcesAssembler.toModel(page, mapper::toOrdemResponseDto));
    }

    @Operation(summary = "Apontar produção",
            description = "Gera N SAIDA_PRODUCAO (um por componente da ficha técnica ativa) + 1 ENTRADA_PRODUCAO e marca a ordem CONCLUIDA.")
    @PostMapping("/ordens/{id}/apontar")
    @PreAuthorize("hasAuthority('PRODUCAO_GERENCIAR')")
    public ResponseEntity<Void> apontar(@PathVariable UUID id, @RequestBody @Valid ApontarProducaoRequestDTO dto) {
        service.apontarProducao(tenantId(), userId(), id, dto.quantidadeProduzida());
        return ResponseEntity.noContent().build();
    }

    private Long tenantId() {
        return SecurityUtils.getCurrentTenantId()
                .orElseThrow(() -> new BusinessException(Constants.TENANT_NOT_FOUND, HttpStatus.UNAUTHORIZED));
    }

    private UUID userId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new BusinessException(Constants.USUARIO_NAO_AUTENTICADO, HttpStatus.UNAUTHORIZED));
    }
}
