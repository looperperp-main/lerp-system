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
import com.l.erp.operacoesservice.domain.vendas.enumerators.TipoItemPedido;
import com.l.erp.operacoesservice.infra.client.CadastroServiceClient;
import com.l.erp.operacoesservice.infra.client.FiscalServiceClient;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** O2C — orçamento → pedido → expedição → faturamento (spec/o2c-vendas.md §5/§10, Fase 4). */
@RestController
@RequestMapping("/api/v1/pedidos")
@Tag(name = "Pedidos", description = "O2C — orçamento → pedido → expedição → faturamento")
public class PedidoController {

    private final Logger logger = LoggerFactory.getLogger(PedidoController.class);
    private final PedidoService service;
    private final CadastroServiceClient cadastroServiceClient;
    private final FiscalServiceClient fiscalServiceClient;
    private final PedidoMapper mapper;
    private final PedidoAssembler assembler;

    public PedidoController(PedidoService service, CadastroServiceClient cadastroServiceClient,
                             FiscalServiceClient fiscalServiceClient,
                             PedidoMapper mapper, PedidoAssembler assembler) {
        this.service = service;
        this.cadastroServiceClient = cadastroServiceClient;
        this.fiscalServiceClient = fiscalServiceClient;
        this.mapper = mapper;
        this.assembler = assembler;
    }

    @Operation(summary = "Criar orçamento",
            description = "Cria um pedido em status ORCAMENTO. Resolve o tipo (mercadoria/serviço) de cada "
                    + "item junto ao cadastro-service e rejeita produto inativo (400).")
    @PostMapping
    @PreAuthorize("hasAuthority('PEDIDO_ESCRITA')")
    public ResponseEntity<PedidoResponseDTO> criar(@RequestBody @Valid PedidoRequestDTO dto) {
        logger.info("Criando orçamento para cliente ID: {}", dto.clienteId());
        Long tenantId = tenantId();
        UUID userId = userId();
        List<PedidoItem> itens = mapper.toItemEntities(dto.itens());
        resolverTiposDosItens(itens, tenantId, userId);
        Pedido salvo = service.criarOrcamento(mapper.toEntity(dto), itens, tenantId, userId);
        PedidoResponseDTO response = detalhe(salvo);
        return ResponseEntity.created(response.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(response);
    }

    @Operation(summary = "Atualizar orçamento", description = "Edita um pedido em status ORCAMENTO (§7).")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PEDIDO_ESCRITA')")
    public ResponseEntity<PedidoResponseDTO> atualizar(@PathVariable UUID id, @RequestBody @Valid PedidoRequestDTO dto) {
        logger.info("Atualizando orçamento ID: {}", id);
        Long tenantId = tenantId();
        UUID userId = userId();
        List<PedidoItem> itens = mapper.toItemEntities(dto.itens());
        resolverTiposDosItens(itens, tenantId, userId);
        Pedido atualizado = service.atualizar(id, tenantId, userId, mapper.toEntity(dto), itens);
        return ResponseEntity.ok(detalhe(atualizado));
    }

    // Resolve o tipo (mercadoria/serviço) de cada item junto ao cadastro-service e rejeita
    // produto inativo — precisa acontecer antes de chamar o service, que já espera tipoItem setado.
    private void resolverTiposDosItens(List<PedidoItem> itens, Long tenantId, UUID userId) {
        for (PedidoItem item : itens) {
            CadastroServiceClient.ProdutoRef ref = cadastroServiceClient.buscarProduto(item.getProdutoId(), tenantId, userId);
            if (Boolean.FALSE.equals(ref.ativo())) {
                throw new BusinessException(String.format(Constants.PEDIDO_PRODUTO_INATIVO, ref.nome()), HttpStatus.BAD_REQUEST);
            }
            item.setTipoItem(TipoItemPedido.valueOf(ref.tipo()));
        }
    }

    @Operation(summary = "Buscar pedido por ID", description = "Detalhe do pedido: itens, parcelas e histórico de status.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PEDIDO_LEITURA')")
    public ResponseEntity<PedidoResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(detalhe(service.buscarPorId(id, tenantId())));
    }

    @Operation(summary = "Listar pedidos", description = "Lista paginada com filtros por status, cliente, vendedor, número e período de emissão.")
    @GetMapping
    @PreAuthorize("hasAuthority('PEDIDO_LEITURA')")
    public ResponseEntity<PagedModel<PedidoResponseDTO>> listar(
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) UUID vendedorId,
            @RequestParam(required = false) Long numero,
            @RequestParam(required = false) LocalDate dataEmissaoDe,
            @RequestParam(required = false) LocalDate dataEmissaoAte,
            Pageable pageable,
            PagedResourcesAssembler<Pedido> pagedResourcesAssembler) {
        Page<Pedido> page = service.listar(
                tenantId(), status, clienteId, vendedorId, numero, dataEmissaoDe, dataEmissaoAte, pageable);
        return ResponseEntity.ok(pagedResourcesAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Confirmar orçamento",
            description = "Transição ORCAMENTO → CONFIRMADO. Checa limite de crédito no cadastro-service, salvo "
                    + "quem tem a authority PEDIDO_CONFIRMACAO_SEM_LIMITE.")
    @PostMapping("/{id}/confirmar")
    @PreAuthorize("hasAuthority('PEDIDO_CONFIRMACAO')")
    public ResponseEntity<PedidoResponseDTO> confirmar(@PathVariable UUID id) {
        logger.info("Confirmando pedido ID: {}", id);
        Long tenantId = tenantId();
        UUID userId = userId();
        boolean semLimite = SecurityUtils.hasAuthority("PEDIDO_CONFIRMACAO_SEM_LIMITE");
        // ponytail: uma consulta a mais pro clienteId (confirmar() também busca o pedido por dentro) —
        // trocar por uma versão que recebe Pedido já carregado só se isso virar hot path (§7).
        Pedido pedido = service.buscarPorId(id, tenantId);
        BigDecimal limiteCredito = cadastroServiceClient.buscarLimiteCredito(pedido.getClienteId(), tenantId, userId);
        return ResponseEntity.ok(detalhe(service.confirmar(id, tenantId, userId, semLimite, limiteCredito)));
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
                    + "pela condição de pagamento do cliente no cadastro-service.")
    @PostMapping("/{id}/faturar")
    @PreAuthorize("hasAuthority('PEDIDO_FATURAMENTO')")
    public ResponseEntity<PedidoResponseDTO> faturar(@PathVariable UUID id) {
        logger.info("Faturando pedido ID: {}", id);
        Long tenantId = tenantId();
        UUID userId = userId();
        Pedido pedido = service.buscarPorId(id, tenantId);
        if (pedido.getCondicaoPagamentoId() == null) {
            throw new BusinessException(Constants.PEDIDO_CONDICAO_PAGAMENTO_OBRIGATORIA, HttpStatus.BAD_REQUEST);
        }
        List<PedidoService.ParcelaDefinicao> parcelasDefinicao =
                cadastroServiceClient.buscarParcelas(pedido.getCondicaoPagamentoId(), tenantId, userId);
        PedidoService.ResultadoFiscalAgregado fiscal = calcularFiscal(id, pedido.getClienteId(), tenantId, userId);
        PedidoService.FaturamentoResultado resultado = service.faturar(id, tenantId, userId, parcelasDefinicao, fiscal);
        return ResponseEntity.ok(assembler.toFaturamentoModel(resultado, service.listarItens(id), service.listarHistorico(id)));
    }

    // D4: chama POST /fiscal/calcular (fiscal-service) por item do pedido e soma o resultado —
    // dataCompetencia = hoje, já que o cálculo só acontece no momento do faturamento (§8).
    // P2: UF/IBGE de destino vêm do endereço fiscal do cliente (buscarEnderecoFiscal), buscado
    // uma vez por pedido (não muda por item).
    // Fase 6 (spec/estabelecimentos-filiais.md §6.1): UF de origem vem do endereço fiscal do
    // estabelecimento "próprio" do tenant, buscado uma vez por pedido (mesmo padrão do destino).
    private PedidoService.ResultadoFiscalAgregado calcularFiscal(UUID pedidoId, UUID clienteId, Long tenantId, UUID userId) {
        LocalDate dataCompetencia = LocalDate.now();
        UUID pessoaId = cadastroServiceClient.buscarClientePessoaId(clienteId, tenantId, userId);
        CadastroServiceClient.EnderecoFiscalRef endereco = pessoaId != null
                ? cadastroServiceClient.buscarEnderecoFiscal(pessoaId, tenantId, userId) : null;
        UUID pessoaIdProprio = cadastroServiceClient.buscarPessoaIdEstabelecimentoProprio(tenantId, userId);
        CadastroServiceClient.EnderecoFiscalRef enderecoOrigem = pessoaIdProprio != null
                ? cadastroServiceClient.buscarEnderecoFiscal(pessoaIdProprio, tenantId, userId) : null;
        String ufOrigem = enderecoOrigem != null ? enderecoOrigem.uf() : null;
        BigDecimal ibs = BigDecimal.ZERO, cbs = BigDecimal.ZERO, is = BigDecimal.ZERO,
                iss = BigDecimal.ZERO, retencoes = BigDecimal.ZERO;
        for (PedidoItem item : service.listarItens(pedidoId)) {
            CadastroServiceClient.ProdutoRef produto = cadastroServiceClient.buscarProduto(item.getProdutoId(), tenantId, userId);
            FiscalServiceClient.ResultadoFiscalItem r = fiscalServiceClient.calcularItem(item, produto, dataCompetencia, tenantId, endereco, ufOrigem);
            ibs = ibs.add(r.valorIbs());
            cbs = cbs.add(r.valorCbs());
            is = is.add(r.valorIs());
            iss = iss.add(r.valorIss());
            retencoes = retencoes.add(r.valorRetencoes());
        }
        return new PedidoService.ResultadoFiscalAgregado(ibs, cbs, is, iss, retencoes);
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
        return ResponseEntity.ok(detalhe(service.recalcularPrecos(id, tenantId())));
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
