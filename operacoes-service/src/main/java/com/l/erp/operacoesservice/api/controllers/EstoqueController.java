package com.l.erp.operacoesservice.api.controllers;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.api.dto.AjusteEstoqueRequestDTO;
import com.l.erp.operacoesservice.api.dto.EstoqueSaldoResponseDTO;
import com.l.erp.operacoesservice.api.dto.MovimentoEstoqueResponseDTO;
import com.l.erp.operacoesservice.api.mappers.EstoqueMapper;
import com.l.erp.operacoesservice.domain.estoque.EstoqueSaldo;
import com.l.erp.operacoesservice.domain.estoque.MovimentoEstoque;
import com.l.erp.operacoesservice.infra.client.CadastroServiceClient;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Saldo e extrato de estoque + ajuste por saldo contado (spec/estoque.md §5, Fases E5-E6). */
@RestController
@RequestMapping("/api/v1/estoque")
@Tag(name = "Estoque", description = "Saldo, extrato de movimentos e ajuste por saldo contado")
public class EstoqueController {

    private final Logger logger = LoggerFactory.getLogger(EstoqueController.class);
    private final EstoqueService service;
    private final EstoqueMapper mapper;
    private final CadastroServiceClient cadastroServiceClient;

    public EstoqueController(EstoqueService service, EstoqueMapper mapper, CadastroServiceClient cadastroServiceClient) {
        this.service = service;
        this.mapper = mapper;
        this.cadastroServiceClient = cadastroServiceClient;
    }

    @Operation(summary = "Listar saldos", description = "Saldo por produto/depósito, com badge estoqueMinimo/abaixoMinimo (consulta best-effort ao cadastro-service).")
    @GetMapping("/saldos")
    @PreAuthorize("hasAuthority('ESTOQUE_VISUALIZAR')")
    public ResponseEntity<PagedModel<EstoqueSaldoResponseDTO>> saldos(
            @RequestParam(required = false) UUID produtoId,
            @RequestParam(required = false) UUID depositoId,
            @RequestParam(defaultValue = "false") boolean comSaldo,
            Pageable pageable,
            PagedResourcesAssembler<EstoqueSaldo> pagedResourcesAssembler) {
        Page<EstoqueSaldo> page = service.buscarSaldos(tenantId(), produtoId, depositoId, comSaldo, pageable);
        PagedModel<EstoqueSaldoResponseDTO> resposta = pagedResourcesAssembler.toModel(page, mapper::toSaldoResponseDto);
        enriquecerComEstoqueMinimo(resposta.getContent());
        return ResponseEntity.ok(resposta);
    }

    // E6 (spec/estoque.md §5.1) — agrupa por depositoId (pouca variação por página) e chama o
    // cadastro-service uma vez por grupo; best-effort, CadastroServiceClient já engole falha e
    // devolve mapa vazio, então o restante da página segue com estoqueMinimo/abaixoMinimo null.
    private void enriquecerComEstoqueMinimo(Collection<EstoqueSaldoResponseDTO> saldos) {
        // groupingBy estoura NPE em chave nula; depositoId nulo (não deveria acontecer com
        // saldo real, mas é defensivo) só fica sem estoqueMinimo/abaixoMinimo preenchido.
        Map<UUID, List<EstoqueSaldoResponseDTO>> porDeposito = saldos.stream()
                .filter(item -> item.getDepositoId() != null)
                .collect(Collectors.groupingBy(EstoqueSaldoResponseDTO::getDepositoId));
        porDeposito.forEach((depositoId, itens) -> {
            List<UUID> produtoIds = itens.stream().map(EstoqueSaldoResponseDTO::getProdutoId).toList();
            Map<UUID, BigDecimal> minimos = cadastroServiceClient.buscarEstoqueConfig(produtoIds, depositoId, tenantId(), userId());
            itens.forEach(item -> {
                BigDecimal minimo = minimos.get(item.getProdutoId());
                item.setEstoqueMinimo(minimo);
                item.setAbaixoMinimo(minimo != null && item.getQuantidade().compareTo(minimo) < 0);
            });
        });
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
