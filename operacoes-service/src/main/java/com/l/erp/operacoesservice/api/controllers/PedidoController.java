package com.l.erp.operacoesservice.api.controllers;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.api.dto.CancelarPedidoRequestDTO;
import com.l.erp.operacoesservice.api.dto.ExpedirPedidoRequestDTO;
import com.l.erp.operacoesservice.api.dto.PedidoRequestDTO;
import com.l.erp.operacoesservice.api.dto.PedidoResponseDTO;
import com.l.erp.operacoesservice.api.mappers.PedidoAssembler;
import com.l.erp.operacoesservice.api.mappers.PedidoMapper;
import com.l.erp.operacoesservice.domain.vendas.Pedido;
import com.l.erp.operacoesservice.domain.vendas.PedidoItem;
import com.l.erp.operacoesservice.domain.vendas.enumerators.StatusPedido;
import com.l.erp.operacoesservice.services.vendas.PedidoService;
import com.l.erp.operacoesservice.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** CRUD + transições de estado do pedido de venda (spec/modulos/o2c-vendas/o2c-vendas.md §5/§10, Fase 4). */
@RestController
@RequestMapping("/api/v1/pedidos")
@Tag(name = "Pedidos")
public class PedidoController {

    private static final Logger logger = LoggerFactory.getLogger(PedidoController.class);

    private final PedidoService service;
    private final PedidoMapper mapper;
    private final PedidoAssembler assembler;
    private final PagedResourcesAssembler<Pedido> pagedResourcesAssembler;

    public PedidoController(PedidoService service, PedidoMapper mapper, PedidoAssembler assembler,
                             PagedResourcesAssembler<Pedido> pagedResourcesAssembler) {
        this.service = service;
        this.mapper = mapper;
        this.assembler = assembler;
        this.pagedResourcesAssembler = pagedResourcesAssembler;
    }

    @Operation(summary = "Criar orçamento", description = "Cria pedido em status ORCAMENTO. Tipo do item "
            + "(mercadoria/serviço) é resolvido no cadastro-service; produto inativo é rejeitado (400).")
    @PostMapping
    @PreAuthorize("hasAuthority('PEDIDO_ESCRITA')")
    public ResponseEntity<PedidoResponseDTO> criar(@RequestBody @Valid PedidoRequestDTO dto) {
        logger.info("Criando orçamento para cliente ID: {}", dto.clienteId());
        List<PedidoItem> itens = mapper.toItemEntities(dto.itens());
        Pedido salvo = service.criarOrcamento(mapper.toEntity(dto), itens, tenantId(), userId());
        PedidoResponseDTO response = detalhe(salvo);
        return ResponseEntity.created(response.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(response);
    }

    @Operation(summary = "Atualizar orçamento", description = "Substitui cabeçalho e itens de um pedido em ORCAMENTO (§7).")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PEDIDO_ESCRITA')")
    public ResponseEntity<PedidoResponseDTO> atualizar(@PathVariable UUID id, @RequestBody @Valid PedidoRequestDTO dto) {
        logger.info("Atualizando orçamento ID: {}", id);
        List<PedidoItem> itens = mapper.toItemEntities(dto.itens());
        Pedido atualizado = service.atualizar(id, tenantId(), userId(), mapper.toEntity(dto), itens);
        return ResponseEntity.ok(detalhe(atualizado));
    }

    @Operation(summary = "Buscar pedido por ID", description = "Detalhe do pedido: itens, parcelas e histórico de status.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PEDIDO_LEITURA')")
    public ResponseEntity<PedidoResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(detalhe(service.buscarPorId(id, tenantId())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PEDIDO_LEITURA')")
    public ResponseEntity<PagedModel<PedidoResponseDTO>> listar(
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) UUID vendedorId,
            @RequestParam(required = false) Long numero,
            @RequestParam(required = false) LocalDate dataEmissaoDe,
            @RequestParam(required = false) LocalDate dataEmissaoAte,
            Pageable pageable) {
        Page<Pedido> page = service.listar(
                tenantId(), status, clienteId, vendedorId, numero, dataEmissaoDe, dataEmissaoAte, pageable);
        return ResponseEntity.ok(pagedResourcesAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Confirmar pedido", description = "Transição ORCAMENTO/BLOQUEADO_CREDITO → CONFIRMADO. "
            + "Checa limite de crédito do cliente no cadastro-service, salvo bypass via PEDIDO_CONFIRMACAO_SEM_LIMITE.")
    @PostMapping("/{id}/confirmar")
    @PreAuthorize("hasAuthority('PEDIDO_CONFIRMACAO')")
    public ResponseEntity<PedidoResponseDTO> confirmar(@PathVariable UUID id) {
        logger.info("Confirmando pedido ID: {}", id);
        boolean semLimite = SecurityUtils.hasAuthority("PEDIDO_CONFIRMACAO_SEM_LIMITE");
        return ResponseEntity.ok(detalhe(service.confirmar(id, tenantId(), userId(), semLimite)));
    }

    @Operation(summary = "Expedir pedido",
            description = "Transição CONFIRMADO → EXPEDIDO, com baixa de estoque. Bloqueada (400) para pedido "
                    + "só de serviço — esse caso vai direto de CONFIRMADO pra FATURADO (§D3).")
    @PostMapping("/{id}/expedir")
    @PreAuthorize("hasAuthority('PEDIDO_EXPEDICAO')")
    public ResponseEntity<PedidoResponseDTO> expedir(@PathVariable UUID id, @RequestBody @Valid ExpedirPedidoRequestDTO dto) {
        logger.info("Expedindo pedido ID: {}", id);
        Pedido expedido = service.expedir(
                id, tenantId(), userId(), dto.depositoId(), dto.transportadoraId(), dto.valorFrete(), dto.modalidadeFrete());
        return ResponseEntity.ok(detalhe(expedido));
    }

    @Operation(summary = "Faturar pedido",
            description = "Transição EXPEDIDO → FATURADO (ou direto de CONFIRMADO, se só serviço). Gera parcelas "
                    + "pela condição de pagamento do cliente no cadastro-service e calcula tributos via fiscal-service.")
    @PostMapping("/{id}/faturar")
    @PreAuthorize("hasAuthority('PEDIDO_FATURAMENTO')")
    public ResponseEntity<PedidoResponseDTO> faturar(@PathVariable UUID id) {
        logger.info("Faturando pedido ID: {}", id);
        PedidoService.FaturamentoResultado resultado = service.faturar(id, tenantId(), userId());
        return ResponseEntity.ok(
                assembler.toFaturamentoModel(resultado, service.listarItens(id), service.listarHistorico(id)));
    }

    @Operation(summary = "Cancelar pedido", description = "Cancela o pedido (motivo obrigatório) em qualquer status anterior a FATURADO.")
    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAuthority('PEDIDO_CANCELAMENTO')")
    public ResponseEntity<PedidoResponseDTO> cancelar(@PathVariable UUID id, @RequestBody @Valid CancelarPedidoRequestDTO dto) {
        logger.info("Cancelando pedido ID: {}", id);
        Pedido cancelado = service.cancelar(id, tenantId(), userId(), dto.motivo());
        return ResponseEntity.ok(detalhe(cancelado));
    }

    @Operation(summary = "Recalcular preços", description = "Reaplica tabela de preços vigente aos itens do pedido em ORCAMENTO.")
    @PostMapping("/{id}/recalcular-precos")
    @PreAuthorize("hasAuthority('PEDIDO_ESCRITA')")
    public ResponseEntity<PedidoResponseDTO> recalcularPrecos(@PathVariable UUID id) {
        return ResponseEntity.ok(detalhe(service.recalcularPrecos(id, tenantId(), userId())));
    }

    @Operation(summary = "Reabrir pedido", description = "Volta o pedido de CONFIRMADO para ORCAMENTO, permitindo nova edição.")
    @PostMapping("/{id}/reabrir")
    @PreAuthorize("hasAuthority('PEDIDO_ESCRITA')")
    public ResponseEntity<PedidoResponseDTO> reabrir(@PathVariable UUID id) {
        logger.info("Reabrindo pedido ID: {}", id);
        return ResponseEntity.ok(detalhe(service.reabrir(id, tenantId(), userId())));
    }

    private PedidoResponseDTO detalhe(Pedido pedido) {
        return assembler.toDetailModel(pedido, service.listarItens(pedido.getId()), service.listarHistorico(pedido.getId()));
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
