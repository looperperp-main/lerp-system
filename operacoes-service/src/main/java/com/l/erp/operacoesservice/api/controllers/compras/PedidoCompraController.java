package com.l.erp.operacoesservice.api.controllers.compras;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.api.dto.CancelarPedidoCompraRequestDTO;
import com.l.erp.operacoesservice.api.dto.EncerrarSaldoPedidoCompraRequestDTO;
import com.l.erp.operacoesservice.api.dto.PedidoCompraRequestDTO;
import com.l.erp.operacoesservice.api.dto.PedidoCompraResponseDTO;
import com.l.erp.operacoesservice.api.dto.ReprovarPedidoCompraRequestDTO;
import com.l.erp.operacoesservice.api.mappers.PedidoCompraAssembler;
import com.l.erp.operacoesservice.api.mappers.PedidoCompraMapper;
import com.l.erp.operacoesservice.domain.compras.PedidoCompra;
import com.l.erp.operacoesservice.domain.compras.PedidoCompraItem;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusPedidoCompra;
import com.l.erp.operacoesservice.services.compras.PedidoCompraService;
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

/** P2P — pedido de compra: RASCUNHO → PENDENTE_APROVACAO → APROVADO → ENVIADO → ... (spec/p2p-compras.md, Fase 2). */
@RestController
@RequestMapping("/api/v1/compras/pedidos")
@Tag(name = "Pedidos de Compra", description = "P2P — pedido de compra e sua máquina de estados")
public class PedidoCompraController {

    private final Logger logger = LoggerFactory.getLogger(PedidoCompraController.class);
    private final PedidoCompraService service;
    private final PedidoCompraMapper mapper;
    private final PedidoCompraAssembler assembler;

    public PedidoCompraController(PedidoCompraService service, PedidoCompraMapper mapper, PedidoCompraAssembler assembler) {
        this.service = service;
        this.mapper = mapper;
        this.assembler = assembler;
    }

    @Operation(summary = "Criar pedido de compra",
            description = "Cria o pedido em status RASCUNHO. Avulso ou originado de uma requisição aprovada "
                    + "(requisicaoId opcional). Valida fornecedor ativo (RN-P2P-02) e alerta preço fora da faixa (RN-P2P-04).")
    @PostMapping
    @PreAuthorize("hasAuthority('COMPRAS_CRIAR')")
    public ResponseEntity<PedidoCompraResponseDTO> criar(@RequestBody @Valid PedidoCompraRequestDTO dto) {
        logger.info("Criando pedido de compra para fornecedor ID: {}", dto.fornecedorId());
        Long tenantId = tenantId();
        UUID userId = userId();
        List<PedidoCompraItem> itens = mapper.toItemEntities(dto.itens());
        PedidoCompra salvo = service.criar(mapper.toEntity(dto), itens, tenantId, userId);
        PedidoCompraResponseDTO response = detalhe(salvo);
        return ResponseEntity.created(response.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(response);
    }

    @Operation(summary = "Atualizar pedido de compra", description = "Edita um pedido em status RASCUNHO.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPRAS_CRIAR')")
    public ResponseEntity<PedidoCompraResponseDTO> atualizar(@PathVariable UUID id,
                                                              @RequestBody @Valid PedidoCompraRequestDTO dto) {
        logger.info("Atualizando pedido de compra ID: {}", id);
        Long tenantId = tenantId();
        UUID userId = userId();
        List<PedidoCompraItem> itens = mapper.toItemEntities(dto.itens());
        PedidoCompra atualizado = service.atualizar(id, tenantId, userId, mapper.toEntity(dto), itens);
        return ResponseEntity.ok(detalhe(atualizado));
    }

    @Operation(summary = "Buscar pedido por ID", description = "Detalhe do pedido: itens e histórico de status.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPRAS_VISUALIZAR')")
    public ResponseEntity<PedidoCompraResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(detalhe(service.buscarPorId(id, tenantId())));
    }

    @Operation(summary = "Listar pedidos de compra",
            description = "Lista paginada com filtros por status, fornecedor e período de data de emissão.")
    @GetMapping
    @PreAuthorize("hasAuthority('COMPRAS_VISUALIZAR')")
    public ResponseEntity<PagedModel<PedidoCompraResponseDTO>> listar(
            @RequestParam(required = false) StatusPedidoCompra status,
            @RequestParam(required = false) UUID fornecedorId,
            @RequestParam(required = false) LocalDate dataEmissaoDe,
            @RequestParam(required = false) LocalDate dataEmissaoAte,
            Pageable pageable,
            PagedResourcesAssembler<PedidoCompra> pagedResourcesAssembler) {
        Page<PedidoCompra> page = service.listar(tenantId(), status, fornecedorId, dataEmissaoDe, dataEmissaoAte, pageable);
        return ResponseEntity.ok(pagedResourcesAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Enviar para aprovação", description = "Transição RASCUNHO → PENDENTE_APROVACAO.")
    @PostMapping("/{id}/enviar-aprovacao")
    @PreAuthorize("hasAuthority('COMPRAS_CRIAR')")
    public ResponseEntity<PedidoCompraResponseDTO> enviarParaAprovacao(@PathVariable UUID id) {
        logger.info("Enviando pedido de compra ID {} para aprovação", id);
        return ResponseEntity.ok(detalhe(service.enviarParaAprovacao(id, tenantId(), userId())));
    }

    @Operation(summary = "Aprovar pedido", description = "Transição PENDENTE_APROVACAO → APROVADO (RN-P2P-01).")
    @PostMapping("/{id}/aprovar")
    @PreAuthorize("hasAuthority('COMPRAS_APROVAR_PEDIDO')")
    public ResponseEntity<PedidoCompraResponseDTO> aprovar(@PathVariable UUID id) {
        logger.info("Aprovando pedido de compra ID: {}", id);
        return ResponseEntity.ok(detalhe(service.aprovar(id, tenantId(), userId())));
    }

    @Operation(summary = "Reprovar pedido", description = "Transição PENDENTE_APROVACAO → REPROVADO (motivo obrigatório).")
    @PostMapping("/{id}/reprovar")
    @PreAuthorize("hasAuthority('COMPRAS_APROVAR_PEDIDO')")
    public ResponseEntity<PedidoCompraResponseDTO> reprovar(@PathVariable UUID id,
                                                             @RequestBody @Valid ReprovarPedidoCompraRequestDTO dto) {
        logger.info("Reprovando pedido de compra ID: {}", id);
        return ResponseEntity.ok(detalhe(service.reprovar(id, tenantId(), userId(), dto.motivo())));
    }

    @Operation(summary = "Reabrir pedido", description = "Transição REPROVADO → RASCUNHO, permitindo correção e reenvio.")
    @PostMapping("/{id}/reabrir")
    @PreAuthorize("hasAuthority('COMPRAS_CRIAR')")
    public ResponseEntity<PedidoCompraResponseDTO> reabrir(@PathVariable UUID id) {
        logger.info("Reabrindo pedido de compra ID: {}", id);
        return ResponseEntity.ok(detalhe(service.reabrir(id, tenantId(), userId())));
    }

    @Operation(summary = "Marcar como enviado ao fornecedor", description = "Transição APROVADO → ENVIADO.")
    @PostMapping("/{id}/enviar")
    @PreAuthorize("hasAuthority('COMPRAS_CRIAR')")
    public ResponseEntity<PedidoCompraResponseDTO> enviar(@PathVariable UUID id) {
        logger.info("Marcando pedido de compra ID {} como enviado ao fornecedor", id);
        return ResponseEntity.ok(detalhe(service.enviar(id, tenantId(), userId())));
    }

    @Operation(summary = "Cancelar pedido",
            description = "Cancela o pedido (motivo obrigatório) a partir de RASCUNHO, PENDENTE_APROVACAO, APROVADO ou ENVIADO.")
    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAuthority('COMPRAS_CRIAR')")
    public ResponseEntity<PedidoCompraResponseDTO> cancelar(@PathVariable UUID id,
                                                             @RequestBody @Valid CancelarPedidoCompraRequestDTO dto) {
        logger.info("Cancelando pedido de compra ID: {}", id);
        return ResponseEntity.ok(detalhe(service.cancelar(id, tenantId(), userId(), dto.motivo())));
    }

    @Operation(summary = "Encerrar saldo do pedido",
            description = "Encerra o saldo pendente (RECEBIDO_PARCIAL/RECEBIDO_TOTAL -> ENCERRADO) quando o comprador decide não receber o restante. Motivo obrigatório.")
    @PostMapping("/{id}/encerrar-saldo")
    @PreAuthorize("hasAuthority('COMPRAS_CRIAR')")
    public ResponseEntity<PedidoCompraResponseDTO> encerrarSaldo(@PathVariable UUID id,
            @RequestBody @Valid EncerrarSaldoPedidoCompraRequestDTO dto) {
        logger.info("Encerrando saldo do pedido de compra ID: {}", id);
        return ResponseEntity.ok(detalhe(service.encerrarSaldo(id, tenantId(), userId(), dto.motivo())));
    }

    private PedidoCompraResponseDTO detalhe(PedidoCompra pedido) {
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
