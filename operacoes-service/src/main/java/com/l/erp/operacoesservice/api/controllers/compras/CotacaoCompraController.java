package com.l.erp.operacoesservice.api.controllers.compras;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.api.dto.CancelarCotacaoRequestDTO;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraRequestDTO;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraRespostaRequestDTO;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraResponseDTO;
import com.l.erp.operacoesservice.api.dto.DeclinarCotacaoFornecedorRequestDTO;
import com.l.erp.operacoesservice.api.dto.EncerrarCotacaoRequestDTO;
import com.l.erp.operacoesservice.api.mappers.CotacaoCompraAssembler;
import com.l.erp.operacoesservice.api.mappers.CotacaoCompraMapper;
import com.l.erp.operacoesservice.domain.compras.CotacaoCompra;
import com.l.erp.operacoesservice.domain.compras.CotacaoCompraItem;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusCotacaoCompra;
import com.l.erp.operacoesservice.services.compras.CotacaoCompraService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** P2P — cotação de compra multi-fornecedor: ABERTA → ENCERRADA/CANCELADA (spec/p2p-compras.md, Fase 5). */
@RestController
@RequestMapping("/api/v1/compras/cotacoes")
@Tag(name = "Cotações de Compra", description = "P2P — cotação multi-fornecedor e sua máquina de estados")
public class CotacaoCompraController {

    private final Logger logger = LoggerFactory.getLogger(CotacaoCompraController.class);
    private final CotacaoCompraService service;
    private final CotacaoCompraMapper mapper;
    private final CotacaoCompraAssembler assembler;

    public CotacaoCompraController(CotacaoCompraService service, CotacaoCompraMapper mapper, CotacaoCompraAssembler assembler) {
        this.service = service;
        this.mapper = mapper;
        this.assembler = assembler;
    }

    @Operation(summary = "Criar cotação de compra",
            description = "Cria a cotação em status ABERTA e convida os fornecedores informados. Avulsa (itens obrigatórios "
                    + "no corpo) ou originada de uma requisição aprovada (requisicaoId opcional, itens copiados dela). "
                    + "Valida cada fornecedor ativo (RN-P2P-02).")
    @PostMapping
    @PreAuthorize("hasAuthority('COMPRAS_CRIAR')")
    public ResponseEntity<CotacaoCompraResponseDTO> criar(@RequestBody @Valid CotacaoCompraRequestDTO dto) {
        logger.info("Criando cotação de compra com {} fornecedor(es) convidado(s)", dto.fornecedorIds().size());
        Long tenantId = tenantId();
        UUID userId = userId();
        List<CotacaoCompraItem> itens = mapper.toItemEntities(dto.itens());
        CotacaoCompra salva = service.criar(mapper.toEntity(dto), itens, dto.fornecedorIds(), tenantId, userId);
        CotacaoCompraResponseDTO response = detalhe(salva, tenantId, userId);
        return ResponseEntity.created(response.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(response);
    }

    @Operation(summary = "Buscar cotação por ID",
            description = "Detalhe da cotação: itens, resposta de cada fornecedor convidado (com ordenação sugerida "
                    + "de desempate) e histórico de status.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPRAS_VISUALIZAR')")
    public ResponseEntity<CotacaoCompraResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(detalhe(service.buscarPorId(id, tenantId()), tenantId(), userId()));
    }

    @Operation(summary = "Listar cotações de compra", description = "Lista paginada com filtros por status e requisição de origem.")
    @GetMapping
    @PreAuthorize("hasAuthority('COMPRAS_VISUALIZAR')")
    public ResponseEntity<PagedModel<CotacaoCompraResponseDTO>> listar(
            @RequestParam(required = false) StatusCotacaoCompra status,
            @RequestParam(required = false) UUID requisicaoId,
            Pageable pageable,
            PagedResourcesAssembler<CotacaoCompra> pagedResourcesAssembler) {
        Page<CotacaoCompra> page = service.listar(tenantId(), status, requisicaoId, pageable);
        return ResponseEntity.ok(pagedResourcesAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Registrar resposta do fornecedor",
            description = "Fornecedor convidado informa preço por item, condição de pagamento, prazo de entrega e frete "
                    + "(condição de pagamento obrigatória — RN-P2P-03). Transição AGUARDANDO → RESPONDIDA.")
    @PostMapping("/{id}/fornecedores/{cotacaoFornecedorId}/responder")
    @PreAuthorize("hasAuthority('COMPRAS_CRIAR')")
    public ResponseEntity<CotacaoCompraResponseDTO> registrarResposta(@PathVariable UUID id,
                                                                       @PathVariable UUID cotacaoFornecedorId,
                                                                       @RequestBody @Valid CotacaoCompraRespostaRequestDTO dto) {
        logger.info("Registrando resposta do fornecedor convite ID {} na cotação ID {}", cotacaoFornecedorId, id);
        Long tenantId = tenantId();
        UUID userId = userId();
        CotacaoCompra cotacao = service.registrarResposta(id, cotacaoFornecedorId, tenantId, userId, dto);
        return ResponseEntity.ok(detalhe(cotacao, tenantId, userId));
    }

    @Operation(summary = "Declinar convite", description = "Fornecedor recusa participar da cotação. Transição AGUARDANDO → DECLINADA.")
    @PostMapping("/{id}/fornecedores/{cotacaoFornecedorId}/declinar")
    @PreAuthorize("hasAuthority('COMPRAS_CRIAR')")
    public ResponseEntity<CotacaoCompraResponseDTO> declinar(@PathVariable UUID id,
                                                              @PathVariable UUID cotacaoFornecedorId,
                                                              @RequestBody(required = false) DeclinarCotacaoFornecedorRequestDTO dto) {
        logger.info("Declinando convite do fornecedor ID {} na cotação ID {}", cotacaoFornecedorId, id);
        Long tenantId = tenantId();
        UUID userId = userId();
        String motivo = dto != null ? dto.motivo() : null;
        CotacaoCompra cotacao = service.declinar(id, cotacaoFornecedorId, tenantId, userId, motivo);
        return ResponseEntity.ok(detalhe(cotacao, tenantId, userId));
    }

    @Operation(summary = "Encerrar cotação",
            description = "Seleciona o fornecedor vencedor (escolha sempre manual) e gera o pedido de compra em RASCUNHO "
                    + "a partir da resposta dele. Transição ABERTA → ENCERRADA.")
    @PostMapping("/{id}/encerrar")
    @PreAuthorize("hasAuthority('COMPRAS_CRIAR')")
    public ResponseEntity<CotacaoCompraResponseDTO> encerrar(@PathVariable UUID id,
                                                              @RequestBody @Valid EncerrarCotacaoRequestDTO dto) {
        logger.info("Encerrando cotação ID {} com fornecedor vencedor convite ID {}", id, dto.cotacaoFornecedorVencedorId());
        Long tenantId = tenantId();
        UUID userId = userId();
        CotacaoCompra cotacao = service.encerrar(id, dto.cotacaoFornecedorVencedorId(), tenantId, userId);
        return ResponseEntity.ok(detalhe(cotacao, tenantId, userId));
    }

    @Operation(summary = "Cancelar cotação", description = "Cancela a cotação (motivo obrigatório) a partir de ABERTA.")
    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAuthority('COMPRAS_CRIAR')")
    public ResponseEntity<CotacaoCompraResponseDTO> cancelar(@PathVariable UUID id,
                                                              @RequestBody @Valid CancelarCotacaoRequestDTO dto) {
        logger.info("Cancelando cotação de compra ID: {}", id);
        Long tenantId = tenantId();
        UUID userId = userId();
        CotacaoCompra cotacao = service.cancelar(id, tenantId, userId, dto.motivo());
        return ResponseEntity.ok(detalhe(cotacao, tenantId, userId));
    }

    private CotacaoCompraResponseDTO detalhe(CotacaoCompra cotacao, Long tenantId, UUID userId) {
        return assembler.toDetailModel(cotacao, service.listarItens(cotacao.getId()),
                service.listarFornecedores(cotacao.getId(), tenantId, userId), service.listarHistorico(cotacao.getId()));
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
