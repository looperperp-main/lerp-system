package com.l.erp.operacoesservice.api.controllers;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.api.dto.AjusteEstoqueRequestDTO;
import com.l.erp.operacoesservice.api.dto.EstoqueSaldoResponseDTO;
import com.l.erp.operacoesservice.api.dto.MovimentoEstoqueResponseDTO;
import com.l.erp.operacoesservice.api.mappers.EstoqueMapper;
import com.l.erp.operacoesservice.domain.estoque.EstoqueSaldo;
import com.l.erp.operacoesservice.domain.estoque.MovimentoEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.OrigemMovimentoEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.TipoMovimentoEstoque;
import com.l.erp.operacoesservice.services.estoque.EstoqueService;
import com.l.erp.operacoesservice.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/** Saldo e extrato de estoque + ajuste por saldo contado (spec/estoque.md §5, Fase E5). */
@RestController
@RequestMapping("/api/v1/estoque")
@Tag(name = "Estoque", description = "Saldo, extrato de movimentos e ajuste por saldo contado")
public class EstoqueController {

    private final Logger logger = LoggerFactory.getLogger(EstoqueController.class);
    private final EstoqueService service;
    private final EstoqueMapper mapper;

    public EstoqueController(EstoqueService service, EstoqueMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Operation(summary = "Listar saldos", description = "Saldo por produto/depósito. estoqueMinimo/abaixoMinimo ficam null até a Fase E6.")
    @GetMapping("/saldos")
    @PreAuthorize("hasAuthority('ESTOQUE_VISUALIZAR')")
    public ResponseEntity<PagedModel<EstoqueSaldoResponseDTO>> saldos(
            @RequestParam(required = false) UUID produtoId,
            @RequestParam(required = false) UUID depositoId,
            @RequestParam(defaultValue = "false") boolean comSaldo,
            Pageable pageable,
            PagedResourcesAssembler<EstoqueSaldo> pagedResourcesAssembler) {
        Page<EstoqueSaldo> page = service.buscarSaldos(tenantId(), produtoId, depositoId, comSaldo, pageable);
        return ResponseEntity.ok(pagedResourcesAssembler.toModel(page, mapper::toSaldoResponseDto));
    }

    @Operation(summary = "Extrato de movimentos", description = "Movimentos ordenados por ocorrido_em DESC, com filtros de produto/depósito/período/tipo/origem.")
    @GetMapping("/movimentos")
    @PreAuthorize("hasAuthority('ESTOQUE_VISUALIZAR')")
    public ResponseEntity<PagedModel<MovimentoEstoqueResponseDTO>> movimentos(
            @RequestParam(required = false) UUID produtoId,
            @RequestParam(required = false) UUID depositoId,
            @RequestParam(required = false) Instant de,
            @RequestParam(required = false) Instant ate,
            @RequestParam(required = false) TipoMovimentoEstoque tipo,
            @RequestParam(required = false) OrigemMovimentoEstoque origemTipo,
            Pageable pageable,
            PagedResourcesAssembler<MovimentoEstoque> pagedResourcesAssembler) {
        Page<MovimentoEstoque> page = service.buscarMovimentos(
                tenantId(), produtoId, depositoId, de, ate, tipo, origemTipo, pageable);
        return ResponseEntity.ok(pagedResourcesAssembler.toModel(page, mapper::toMovimentoResponseDto));
    }

    @Operation(summary = "Ajustar por saldo contado",
            description = "D5: um endpoint só, por saldo contado (não pela diferença). delta == 0 é no-op (200 sem gravar movimento).")
    @PostMapping("/ajustes")
    @PreAuthorize("hasAuthority('ESTOQUE_AJUSTAR')")
    public ResponseEntity<Void> ajustar(@RequestBody @Valid AjusteEstoqueRequestDTO dto) {
        logger.info("Ajuste de estoque: produto {}, depósito {}", dto.produtoId(), dto.depositoId());
        service.ajustar(new EstoqueService.AjusteRequisicao(tenantId(), userId(), dto.produtoId(), dto.depositoId(),
                dto.quantidadeContada(), dto.origem(), dto.motivo(), dto.valorUnitario()));
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
