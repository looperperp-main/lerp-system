package com.l.erp.operacoesservice.api.controllers.compras;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.api.dto.CancelarRecebimentoRequestDTO;
import com.l.erp.operacoesservice.api.dto.RecebimentoMercadoriaRequestDTO;
import com.l.erp.operacoesservice.api.dto.RecebimentoMercadoriaResponseDTO;
import com.l.erp.operacoesservice.api.mappers.RecebimentoMercadoriaAssembler;
import com.l.erp.operacoesservice.api.mappers.RecebimentoMercadoriaMapper;
import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoria;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusRecebimentoMercadoria;
import com.l.erp.operacoesservice.services.compras.RecebimentoMercadoriaService;
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
import java.util.UUID;

/** Recebimento de mercadoria: EM_CONFERENCIA -> CONFIRMADO/CANCELADO (spec/p2p-compras.md, Fase 3). */
@RestController
@RequestMapping("/api/v1/compras")
@Tag(name = "Recebimentos de Compra")
public class RecebimentoMercadoriaController {

    private static final Logger logger = LoggerFactory.getLogger(RecebimentoMercadoriaController.class);

    private final RecebimentoMercadoriaService service;
    private final RecebimentoMercadoriaMapper mapper;
    private final RecebimentoMercadoriaAssembler assembler;

    public RecebimentoMercadoriaController(RecebimentoMercadoriaService service, RecebimentoMercadoriaMapper mapper,
                                            RecebimentoMercadoriaAssembler assembler) {
        this.service = service;
        this.mapper = mapper;
        this.assembler = assembler;
    }

    @Operation(summary = "Registrar recebimento", description = "Cria recebimento em EM_CONFERENCIA para um pedido ENVIADO/RECEBIDO_PARCIAL.")
    @PostMapping("/pedidos/{pedidoId}/recebimentos")
    @PreAuthorize("hasAuthority('COMPRAS_RECEBER')")
    public ResponseEntity<RecebimentoMercadoriaResponseDTO> criar(@PathVariable UUID pedidoId,
            @RequestBody @Valid RecebimentoMercadoriaRequestDTO dto) {
        logger.info("Registrando recebimento para pedido de compra ID: {}", pedidoId);
        RecebimentoMercadoria criado = service.criar(pedidoId, mapper.toEntity(dto), dto.itens(), tenantId(), userId());
        RecebimentoMercadoriaResponseDTO response = detalhe(criado);
        return ResponseEntity.created(response.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(response);
    }

    @Operation(summary = "Editar recebimento", description = "Edita o recebimento — só permitido enquanto EM_CONFERENCIA.")
    @PutMapping("/recebimentos/{id}")
    @PreAuthorize("hasAuthority('COMPRAS_RECEBER')")
    public ResponseEntity<RecebimentoMercadoriaResponseDTO> atualizar(@PathVariable UUID id,
            @RequestBody @Valid RecebimentoMercadoriaRequestDTO dto) {
        logger.info("Atualizando recebimento de mercadoria ID: {}", id);
        RecebimentoMercadoria atualizado = service.atualizar(id, tenantId(), userId(), mapper.toEntity(dto), dto.itens());
        return ResponseEntity.ok(detalhe(atualizado));
    }

    @Operation(summary = "Buscar recebimento por ID")
    @GetMapping("/recebimentos/{id}")
    @PreAuthorize("hasAuthority('COMPRAS_VISUALIZAR')")
    public ResponseEntity<RecebimentoMercadoriaResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(detalhe(service.buscarPorId(id, tenantId())));
    }

    @Operation(summary = "Listar recebimentos", description = "Lista paginada com filtros: status, pedido, período.")
    @GetMapping("/recebimentos")
    @PreAuthorize("hasAuthority('COMPRAS_VISUALIZAR')")
    public ResponseEntity<PagedModel<RecebimentoMercadoriaResponseDTO>> listar(
            @RequestParam(required = false) StatusRecebimentoMercadoria status,
            @RequestParam(required = false) UUID pedidoId,
            @RequestParam(required = false) LocalDate dataRecebimentoDe,
            @RequestParam(required = false) LocalDate dataRecebimentoAte,
            Pageable pageable, PagedResourcesAssembler<RecebimentoMercadoria> pagedResourcesAssembler) {
        Page<RecebimentoMercadoria> page = service.listar(tenantId(), status, pedidoId,
                dataRecebimentoDe, dataRecebimentoAte, pageable);
        return ResponseEntity.ok(pagedResourcesAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Confirmar recebimento",
            description = "Transição EM_CONFERENCIA -> CONFIRMADO: valida RN-P2P-05, baixa estoque (itens de mercadoria) e recalcula status do pedido.")
    @PostMapping("/recebimentos/{id}/confirmar")
    @PreAuthorize("hasAuthority('COMPRAS_RECEBER')")
    public ResponseEntity<RecebimentoMercadoriaResponseDTO> confirmar(@PathVariable UUID id) {
        logger.info("Confirmando recebimento de mercadoria ID: {}", id);
        return ResponseEntity.ok(detalhe(service.confirmar(id, tenantId(), userId())));
    }

    @Operation(summary = "Cancelar recebimento",
            description = "EM_CONFERENCIA/CONFIRMADO -> CANCELADO. Cancelamento de CONFIRMADO estorna o estoque e devolve quantidade_recebida do pedido.")
    @PostMapping("/recebimentos/{id}/cancelar")
    @PreAuthorize("hasAuthority('COMPRAS_RECEBER')")
    public ResponseEntity<RecebimentoMercadoriaResponseDTO> cancelar(@PathVariable UUID id,
            @RequestBody(required = false) CancelarRecebimentoRequestDTO dto) {
        logger.info("Cancelando recebimento de mercadoria ID: {}", id);
        String motivo = dto != null ? dto.motivo() : null;
        return ResponseEntity.ok(detalhe(service.cancelar(id, tenantId(), userId(), motivo)));
    }

    private RecebimentoMercadoriaResponseDTO detalhe(RecebimentoMercadoria recebimento) {
        return assembler.toDetailModel(recebimento, service.listarItens(recebimento.getId()));
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
