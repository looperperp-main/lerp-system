package com.l.erp.operacoesservice.api.controllers.compras;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.api.dto.CancelarRequisicaoRequestDTO;
import com.l.erp.operacoesservice.api.dto.ReprovarRequisicaoRequestDTO;
import com.l.erp.operacoesservice.api.dto.RequisicaoCompraRequestDTO;
import com.l.erp.operacoesservice.api.dto.RequisicaoCompraResponseDTO;
import com.l.erp.operacoesservice.api.mappers.RequisicaoCompraAssembler;
import com.l.erp.operacoesservice.api.mappers.RequisicaoCompraMapper;
import com.l.erp.operacoesservice.domain.compras.RequisicaoCompra;
import com.l.erp.operacoesservice.domain.compras.RequisicaoCompraItem;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusRequisicaoCompra;
import com.l.erp.operacoesservice.infra.client.CadastroServiceClient;
import com.l.erp.operacoesservice.services.compras.RequisicaoCompraService;
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

/** P2P — requisição de compra: RASCUNHO → PENDENTE_APROVACAO → APROVADA/REPROVADA (spec/p2p-compras.md, Fase 1b). */
@RestController
@RequestMapping("/api/v1/compras/requisicoes")
@Tag(name = "Requisições de Compra", description = "P2P — requisição de compra e sua máquina de estados")
public class RequisicaoCompraController {

    private final Logger logger = LoggerFactory.getLogger(RequisicaoCompraController.class);
    private final RequisicaoCompraService service;
    private final CadastroServiceClient cadastroServiceClient;
    private final RequisicaoCompraMapper mapper;
    private final RequisicaoCompraAssembler assembler;

    public RequisicaoCompraController(RequisicaoCompraService service, CadastroServiceClient cadastroServiceClient,
                                       RequisicaoCompraMapper mapper, RequisicaoCompraAssembler assembler) {
        this.service = service;
        this.cadastroServiceClient = cadastroServiceClient;
        this.mapper = mapper;
        this.assembler = assembler;
    }

    @Operation(summary = "Criar requisição de compra",
            description = "Cria a requisição em status RASCUNHO. Resolve o tipo (mercadoria/serviço) de cada "
                    + "item junto ao cadastro-service e rejeita produto inativo (400).")
    @PostMapping
    @PreAuthorize("hasAuthority('COMPRAS_CRIAR')")
    public ResponseEntity<RequisicaoCompraResponseDTO> criar(@RequestBody @Valid RequisicaoCompraRequestDTO dto) {
        logger.info("Criando requisição de compra para solicitante ID: {}", dto.solicitanteId());
        Long tenantId = tenantId();
        UUID userId = userId();
        List<RequisicaoCompraItem> itens = mapper.toItemEntities(dto.itens());
        boolean temItemMercadoria = resolverTemItemMercadoria(itens, tenantId, userId);
        RequisicaoCompra salva = service.criar(mapper.toEntity(dto), itens, tenantId, userId, temItemMercadoria);
        RequisicaoCompraResponseDTO response = detalhe(salva);
        return ResponseEntity.created(response.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(response);
    }

    @Operation(summary = "Atualizar requisição de compra", description = "Edita uma requisição em status RASCUNHO.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPRAS_CRIAR')")
    public ResponseEntity<RequisicaoCompraResponseDTO> atualizar(@PathVariable UUID id,
                                                                   @RequestBody @Valid RequisicaoCompraRequestDTO dto) {
        logger.info("Atualizando requisição de compra ID: {}", id);
        Long tenantId = tenantId();
        UUID userId = userId();
        List<RequisicaoCompraItem> itens = mapper.toItemEntities(dto.itens());
        boolean temItemMercadoria = resolverTemItemMercadoria(itens, tenantId, userId);
        RequisicaoCompra atualizada = service.atualizar(id, tenantId, userId, mapper.toEntity(dto), itens, temItemMercadoria);
        return ResponseEntity.ok(detalhe(atualizada));
    }

    // Resolve o tipo (mercadoria/serviço) de cada item junto ao cadastro-service, rejeita produto
    // inativo e informa se a requisição tem ao menos um item de mercadoria (depositoId obrigatório
    // nesse caso — validado pelo service). Mesmo padrão de PedidoController.resolverTiposDosItens.
    private boolean resolverTemItemMercadoria(List<RequisicaoCompraItem> itens, Long tenantId, UUID userId) {
        boolean temItemMercadoria = false;
        for (RequisicaoCompraItem item : itens) {
            CadastroServiceClient.ProdutoRef ref = cadastroServiceClient.buscarProduto(item.getProdutoId(), tenantId, userId);
            if (Boolean.FALSE.equals(ref.ativo())) {
                throw new BusinessException(String.format(Constants.PEDIDO_PRODUTO_INATIVO, ref.nome()), HttpStatus.BAD_REQUEST);
            }
            if ("MERCADORIA".equals(ref.tipo())) {
                temItemMercadoria = true;
            }
        }
        return temItemMercadoria;
    }

    @Operation(summary = "Buscar requisição por ID", description = "Detalhe da requisição: itens e histórico de status.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPRAS_VISUALIZAR')")
    public ResponseEntity<RequisicaoCompraResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(detalhe(service.buscarPorId(id, tenantId())));
    }

    @Operation(summary = "Listar requisições de compra",
            description = "Lista paginada com filtros por status, solicitante e período de data de necessidade.")
    @GetMapping
    @PreAuthorize("hasAuthority('COMPRAS_VISUALIZAR')")
    public ResponseEntity<PagedModel<RequisicaoCompraResponseDTO>> listar(
            @RequestParam(required = false) StatusRequisicaoCompra status,
            @RequestParam(required = false) UUID solicitanteId,
            @RequestParam(required = false) LocalDate dataNecessidadeDe,
            @RequestParam(required = false) LocalDate dataNecessidadeAte,
            Pageable pageable,
            PagedResourcesAssembler<RequisicaoCompra> pagedResourcesAssembler) {
        Page<RequisicaoCompra> page = service.listar(
                tenantId(), status, solicitanteId, dataNecessidadeDe, dataNecessidadeAte, pageable);
        return ResponseEntity.ok(pagedResourcesAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Enviar para aprovação", description = "Transição RASCUNHO → PENDENTE_APROVACAO.")
    @PostMapping("/{id}/enviar-aprovacao")
    @PreAuthorize("hasAuthority('COMPRAS_CRIAR')")
    public ResponseEntity<RequisicaoCompraResponseDTO> enviarParaAprovacao(@PathVariable UUID id) {
        logger.info("Enviando requisição de compra ID {} para aprovação", id);
        return ResponseEntity.ok(detalhe(service.enviarParaAprovacao(id, tenantId(), userId())));
    }

    @Operation(summary = "Aprovar requisição",
            description = "Transição PENDENTE_APROVACAO → APROVADA. Solicitante pode aprovar o próprio "
                    + "documento no MVP (RN-P2P-01).")
    @PostMapping("/{id}/aprovar")
    @PreAuthorize("hasAuthority('COMPRAS_APROVAR_PEDIDO')")
    public ResponseEntity<RequisicaoCompraResponseDTO> aprovar(@PathVariable UUID id) {
        logger.info("Aprovando requisição de compra ID: {}", id);
        return ResponseEntity.ok(detalhe(service.aprovar(id, tenantId(), userId())));
    }

    @Operation(summary = "Reprovar requisição", description = "Transição PENDENTE_APROVACAO → REPROVADA (motivo obrigatório).")
    @PostMapping("/{id}/reprovar")
    @PreAuthorize("hasAuthority('COMPRAS_APROVAR_PEDIDO')")
    public ResponseEntity<RequisicaoCompraResponseDTO> reprovar(@PathVariable UUID id,
                                                                  @RequestBody @Valid ReprovarRequisicaoRequestDTO dto) {
        logger.info("Reprovando requisição de compra ID: {}", id);
        return ResponseEntity.ok(detalhe(service.reprovar(id, tenantId(), userId(), dto.motivo())));
    }

    @Operation(summary = "Cancelar requisição",
            description = "Cancela a requisição (motivo obrigatório) a partir de RASCUNHO, PENDENTE_APROVACAO ou APROVADA.")
    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAuthority('COMPRAS_CRIAR')")
    public ResponseEntity<RequisicaoCompraResponseDTO> cancelar(@PathVariable UUID id,
                                                                  @RequestBody @Valid CancelarRequisicaoRequestDTO dto) {
        logger.info("Cancelando requisição de compra ID: {}", id);
        return ResponseEntity.ok(detalhe(service.cancelar(id, tenantId(), userId(), dto.motivo())));
    }

    @Operation(summary = "Reabrir requisição", description = "Transição REPROVADA → RASCUNHO, permitindo correção e reenvio.")
    @PostMapping("/{id}/reabrir")
    @PreAuthorize("hasAuthority('COMPRAS_CRIAR')")
    public ResponseEntity<RequisicaoCompraResponseDTO> reabrir(@PathVariable UUID id) {
        logger.info("Reabrindo requisição de compra ID: {}", id);
        return ResponseEntity.ok(detalhe(service.reabrir(id, tenantId(), userId())));
    }

    private RequisicaoCompraResponseDTO detalhe(RequisicaoCompra requisicao) {
        return assembler.toDetailModel(
                requisicao, service.listarItens(requisicao.getId()), service.listarHistorico(requisicao.getId()));
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
