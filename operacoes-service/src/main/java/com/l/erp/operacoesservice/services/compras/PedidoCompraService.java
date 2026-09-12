package com.l.erp.operacoesservice.services.compras;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.domain.compras.CompraStatusHistorico;
import com.l.erp.operacoesservice.domain.compras.PedidoCompra;
import com.l.erp.operacoesservice.domain.compras.PedidoCompraItem;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusPedidoCompra;
import com.l.erp.operacoesservice.domain.compras.enumerators.TipoDocumentoCompra;
import com.l.erp.operacoesservice.infra.client.CadastroServiceClient;
import com.l.erp.operacoesservice.repository.compras.CompraStatusHistoricoRepository;
import com.l.erp.operacoesservice.repository.compras.PedidoCompraItemRepository;
import com.l.erp.operacoesservice.repository.compras.PedidoCompraRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Pedido de compra: RASCUNHO → PENDENTE_APROVACAO → APROVADO → ENVIADO → RECEBIDO_* → ENCERRADO,
 * com REPROVADO/CANCELADO nos pontos previstos (spec/p2p-compras.md, Fase 2). Mesmo padrão de
 * RequisicaoCompraService (Fase 1b). As transições disparadas por recebimento (RECEBIDO_PARCIAL,
 * RECEBIDO_TOTAL) são recalculadas por {@link #recalcularStatusAposRecebimento} — chamado só por
 * RecebimentoMercadoriaService (Fase 3), nunca via endpoint direto. ENCERRADO tem endpoint próprio
 * ({@link #encerrarSaldo}), também Fase 3.
 */
@Service
public class PedidoCompraService {

    private static final Logger log = LoggerFactory.getLogger(PedidoCompraService.class);

    private static final Map<StatusPedidoCompra, Set<StatusPedidoCompra>> TRANSICOES_VALIDAS = Map.of(
            StatusPedidoCompra.RASCUNHO,
            Set.of(StatusPedidoCompra.PENDENTE_APROVACAO, StatusPedidoCompra.CANCELADO),
            StatusPedidoCompra.PENDENTE_APROVACAO,
            Set.of(StatusPedidoCompra.APROVADO, StatusPedidoCompra.REPROVADO, StatusPedidoCompra.CANCELADO),
            StatusPedidoCompra.REPROVADO,
            Set.of(StatusPedidoCompra.RASCUNHO),
            StatusPedidoCompra.APROVADO,
            Set.of(StatusPedidoCompra.ENVIADO, StatusPedidoCompra.CANCELADO),
            StatusPedidoCompra.ENVIADO,
            Set.of(StatusPedidoCompra.RECEBIDO_PARCIAL, StatusPedidoCompra.RECEBIDO_TOTAL, StatusPedidoCompra.CANCELADO),
            StatusPedidoCompra.RECEBIDO_PARCIAL,
            Set.of(StatusPedidoCompra.RECEBIDO_TOTAL, StatusPedidoCompra.ENVIADO, StatusPedidoCompra.ENCERRADO),
            StatusPedidoCompra.RECEBIDO_TOTAL,
            // Fase 3: RECEBIDO_TOTAL -> ENVIADO cobre o cancelamento do único recebimento
            // CONFIRMADO que completava o pedido (quantidade_recebida volta a 0 em todos os itens).
            Set.of(StatusPedidoCompra.RECEBIDO_PARCIAL, StatusPedidoCompra.ENVIADO, StatusPedidoCompra.ENCERRADO),
            StatusPedidoCompra.ENCERRADO, Set.of(),
            StatusPedidoCompra.CANCELADO, Set.of()
    );

    private final PedidoCompraRepository pedidoCompraRepository;
    private final PedidoCompraItemRepository pedidoCompraItemRepository;
    private final CompraStatusHistoricoRepository compraStatusHistoricoRepository;
    private final CompraNumeroService compraNumeroService;
    private final CadastroServiceClient cadastroServiceClient;

    public PedidoCompraService(PedidoCompraRepository pedidoCompraRepository,
                                PedidoCompraItemRepository pedidoCompraItemRepository,
                                CompraStatusHistoricoRepository compraStatusHistoricoRepository,
                                CompraNumeroService compraNumeroService,
                                CadastroServiceClient cadastroServiceClient) {
        this.pedidoCompraRepository = pedidoCompraRepository;
        this.pedidoCompraItemRepository = pedidoCompraItemRepository;
        this.compraStatusHistoricoRepository = compraStatusHistoricoRepository;
        this.compraNumeroService = compraNumeroService;
        this.cadastroServiceClient = cadastroServiceClient;
    }

    // ---------------------------------------------------------------- criação/edição

    @Transactional
    public PedidoCompra criar(PedidoCompra pedido, List<PedidoCompraItem> itens, Long tenantId, UUID userId) {
        validarItens(itens);
        validarCondicaoPagamento(pedido.getCondicaoPagamentoId());
        validarDataPrevisao(pedido.getDataPrevisaoEntrega());
        validarFornecedorAtivo(pedido.getFornecedorId(), tenantId, userId);
        recalcularValores(pedido, itens);
        alertarPrecoForaFaixa(itens, pedido.getFornecedorId(), tenantId, userId);

        Instant agora = Instant.now();
        pedido.setTenantId(tenantId);
        pedido.setNumero(compraNumeroService.proximoNumero(tenantId, TipoDocumentoCompra.PEDIDO));
        pedido.setStatus(StatusPedidoCompra.RASCUNHO);
        pedido.setCreatedAt(agora);
        pedido.setCreatedBy(userId);

        PedidoCompra salvo = pedidoCompraRepository.save(pedido);
        for (PedidoCompraItem item : itens) {
            item.setPedido(salvo);
            item.setTenantId(tenantId);
            item.setCreatedAt(agora);
            item.setCreatedBy(userId);
        }
        pedidoCompraItemRepository.saveAll(itens);

        registrarHistorico(salvo, null, StatusPedidoCompra.RASCUNHO, null, userId, agora);
        return salvo;
    }

    @Transactional
    public PedidoCompra atualizar(UUID pedidoId, Long tenantId, UUID userId, PedidoCompra dados,
                                   List<PedidoCompraItem> itens) {
        PedidoCompra pedido = buscarPedido(pedidoId, tenantId);
        if (pedido.getStatus() != StatusPedidoCompra.RASCUNHO) {
            throw new BusinessException(Constants.PEDIDO_COMPRA_UPDATE_SO_RASCUNHO, HttpStatus.BAD_REQUEST);
        }
        validarItens(itens);
        validarCondicaoPagamento(dados.getCondicaoPagamentoId());
        validarDataPrevisao(dados.getDataPrevisaoEntrega());
        validarFornecedorAtivo(dados.getFornecedorId(), tenantId, userId);

        pedido.setFornecedorId(dados.getFornecedorId());
        pedido.setCondicaoPagamentoId(dados.getCondicaoPagamentoId());
        pedido.setDepositoId(dados.getDepositoId());
        pedido.setRequisicaoId(dados.getRequisicaoId());
        pedido.setDataPrevisaoEntrega(dados.getDataPrevisaoEntrega());
        pedido.setValorFrete(dados.getValorFrete());
        pedido.setObservacao(dados.getObservacao());
        recalcularValores(pedido, itens);
        alertarPrecoForaFaixa(itens, pedido.getFornecedorId(), tenantId, userId);

        Instant agora = Instant.now();
        pedidoCompraItemRepository.deleteAllByPedidoId(pedidoId);
        for (PedidoCompraItem item : itens) {
            item.setPedido(pedido);
            item.setTenantId(tenantId);
            item.setCreatedAt(agora);
            item.setCreatedBy(userId);
        }
        pedidoCompraItemRepository.saveAll(itens);

        pedido.setUpdatedAt(agora);
        pedido.setLastUpdatedBy(userId);
        return pedidoCompraRepository.save(pedido);
    }

    // Chamado por CotacaoCompraService (Fase 5) ao encerrar uma cotação — vincula o pedido recém
    // gerado à resposta vencedora, pra rastreabilidade (spec/p2p-compras.md §"pedido_compra").
    @Transactional
    public PedidoCompra vincularCotacaoFornecedor(UUID pedidoId, UUID cotacaoFornecedorId, Long tenantId) {
        PedidoCompra pedido = buscarPedido(pedidoId, tenantId);
        pedido.setCotacaoFornecedorId(cotacaoFornecedorId);
        return pedidoCompraRepository.save(pedido);
    }

    // ---------------------------------------------------------------- transições de estado

    @Transactional
    public PedidoCompra enviarParaAprovacao(UUID pedidoId, Long tenantId, UUID userId) {
        return transicionar(pedidoId, tenantId, userId, StatusPedidoCompra.PENDENTE_APROVACAO, null, p -> { });
    }

    @Transactional
    public PedidoCompra aprovar(UUID pedidoId, Long tenantId, UUID userId) {
        Instant agora = Instant.now();
        return transicionar(pedidoId, tenantId, userId, StatusPedidoCompra.APROVADO, null, p -> {
            p.setAprovadorId(userId);
            p.setAprovadoEm(agora);
        });
    }

    @Transactional
    public PedidoCompra reprovar(UUID pedidoId, Long tenantId, UUID userId, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException(Constants.PEDIDO_COMPRA_MOTIVO_REPROVACAO_OBRIGATORIO, HttpStatus.BAD_REQUEST);
        }
        return transicionar(pedidoId, tenantId, userId, StatusPedidoCompra.REPROVADO, motivo, p -> { });
    }

    @Transactional
    public PedidoCompra reabrir(UUID pedidoId, Long tenantId, UUID userId) {
        return transicionar(pedidoId, tenantId, userId, StatusPedidoCompra.RASCUNHO, null, p -> { });
    }

    @Transactional
    public PedidoCompra enviar(UUID pedidoId, Long tenantId, UUID userId) {
        LocalDate hoje = LocalDate.now();
        return transicionar(pedidoId, tenantId, userId, StatusPedidoCompra.ENVIADO, null, p -> p.setDataEmissao(hoje));
    }

    @Transactional
    public PedidoCompra cancelar(UUID pedidoId, Long tenantId, UUID userId, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException(Constants.PEDIDO_COMPRA_MOTIVO_CANCELAMENTO_OBRIGATORIO, HttpStatus.BAD_REQUEST);
        }
        return transicionar(pedidoId, tenantId, userId, StatusPedidoCompra.CANCELADO, motivo, p -> p.setMotivoCancelamento(motivo));
    }

    /**
     * Recalcula o status do pedido a partir das quantidades recebidas acumuladas nos itens —
     * chamado por RecebimentoMercadoriaService logo após confirmar ou cancelar um recebimento
     * (spec/p2p-compras.md §"Integração com estoque", Fase 3). No-op quando o status recalculado
     * já é o atual (ex.: mais um recebimento parcial que ainda não completa o pedido).
     */
    @Transactional
    public PedidoCompra recalcularStatusAposRecebimento(UUID pedidoId, Long tenantId, UUID userId) {
        PedidoCompra pedido = buscarPedido(pedidoId, tenantId);
        List<PedidoCompraItem> itens = pedidoCompraItemRepository.findAllByPedidoId(pedidoId);
        boolean algumRecebido = itens.stream().anyMatch(i -> i.getQuantidadeRecebida().signum() > 0);
        boolean todosCompletos = itens.stream()
                .allMatch(i -> i.getQuantidadeRecebida().compareTo(i.getQuantidade()) >= 0);
        StatusPedidoCompra statusRecalculado = todosCompletos ? StatusPedidoCompra.RECEBIDO_TOTAL
                : algumRecebido ? StatusPedidoCompra.RECEBIDO_PARCIAL : StatusPedidoCompra.ENVIADO;
        if (statusRecalculado == pedido.getStatus()) {
            return pedido;
        }
        return transicionar(pedidoId, tenantId, userId, statusRecalculado, null, p -> { });
    }

    /**
     * Encerra o saldo pendente do pedido quando o comprador decide não receber o restante
     * (RECEBIDO_PARCIAL ou RECEBIDO_TOTAL -> ENCERRADO, motivo obrigatório). Endpoint reservado
     * desde a Fase 2 (spec/p2p-compras.md) — habilitado agora porque só faz sentido depois de
     * existir recebimento.
     */
    @Transactional
    public PedidoCompra encerrarSaldo(UUID pedidoId, Long tenantId, UUID userId, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException(Constants.PEDIDO_COMPRA_MOTIVO_ENCERRAMENTO_OBRIGATORIO, HttpStatus.BAD_REQUEST);
        }
        return transicionar(pedidoId, tenantId, userId, StatusPedidoCompra.ENCERRADO, motivo, p -> { });
    }

    private PedidoCompra transicionar(UUID pedidoId, Long tenantId, UUID userId, StatusPedidoCompra statusDestino,
                                       String motivo, Consumer<PedidoCompra> aplicarCampos) {
        PedidoCompra pedido = buscarPedido(pedidoId, tenantId);
        StatusPedidoCompra statusAnterior = pedido.getStatus();
        validarTransicao(statusAnterior, statusDestino);
        Instant agora = Instant.now();
        pedido.setStatus(statusDestino);
        aplicarCampos.accept(pedido);
        pedido.setUpdatedAt(agora);
        pedido.setLastUpdatedBy(userId);
        pedidoCompraRepository.save(pedido);
        registrarHistorico(pedido, statusAnterior, statusDestino, motivo, userId, agora);
        return pedido;
    }

    // ---------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public PedidoCompra buscarPorId(UUID pedidoId, Long tenantId) {
        return buscarPedido(pedidoId, tenantId);
    }

    @Transactional(readOnly = true)
    public Page<PedidoCompra> listar(Long tenantId, StatusPedidoCompra status, UUID fornecedorId,
                                      LocalDate dataEmissaoDe, LocalDate dataEmissaoAte, Pageable pageable) {
        return pedidoCompraRepository.buscarComFiltros(tenantId, status, fornecedorId, dataEmissaoDe, dataEmissaoAte, pageable);
    }

    @Transactional(readOnly = true)
    public List<PedidoCompraItem> listarItens(UUID pedidoId) {
        return pedidoCompraItemRepository.findAllByPedidoId(pedidoId);
    }

    @Transactional(readOnly = true)
    public List<CompraStatusHistorico> listarHistorico(UUID pedidoId) {
        return compraStatusHistoricoRepository.findAllByDocumentoTipoAndDocumentoIdOrderByOcorridoEmAsc(
                TipoDocumentoCompra.PEDIDO, pedidoId);
    }

    // ---------------------------------------------------------------- helpers

    private void validarItens(List<PedidoCompraItem> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new BusinessException(Constants.PEDIDO_COMPRA_SEM_ITENS, HttpStatus.BAD_REQUEST);
        }
    }

    private void validarCondicaoPagamento(UUID condicaoPagamentoId) {
        if (condicaoPagamentoId == null) {
            throw new BusinessException(Constants.PEDIDO_COMPRA_CONDICAO_PAGAMENTO_OBRIGATORIA, HttpStatus.BAD_REQUEST);
        }
    }

    private void validarDataPrevisao(LocalDate dataPrevisaoEntrega) {
        if (dataPrevisaoEntrega != null && dataPrevisaoEntrega.isBefore(LocalDate.now())) {
            throw new BusinessException(Constants.PEDIDO_COMPRA_DATA_PREVISAO_INVALIDA, HttpStatus.BAD_REQUEST);
        }
    }

    // RN-P2P-02: fornecedor precisa estar ativo na emissão do pedido.
    private void validarFornecedorAtivo(UUID fornecedorId, Long tenantId, UUID userId) {
        CadastroServiceClient.FornecedorRef fornecedor = cadastroServiceClient.buscarFornecedor(fornecedorId, tenantId, userId);
        if (Boolean.FALSE.equals(fornecedor.ativo())) {
            throw new BusinessException(
                    String.format(Constants.PEDIDO_COMPRA_FORNECEDOR_INATIVO, fornecedor.pessoaNomeRazao()),
                    HttpStatus.BAD_REQUEST);
        }
    }

    // RN-P2P-04: alerta (não bloqueia) quando o preço do item foge da faixa aceitável em relação ao
    // preco_custo cadastrado pro fornecedor — tolerância de 30% (Constants.PEDIDO_COMPRA_TOLERANCIA_PRECO_ALERTA).
    // Best-effort: se o cadastro-service não devolver o vínculo/custo, o item simplesmente não é avaliado.
    private void alertarPrecoForaFaixa(List<PedidoCompraItem> itens, UUID fornecedorId, Long tenantId, UUID userId) {
        List<UUID> produtoIds = itens.stream().map(PedidoCompraItem::getProdutoId).toList();
        Map<UUID, BigDecimal> precosCusto = cadastroServiceClient.buscarPrecosCusto(produtoIds, fornecedorId, tenantId, userId);
        for (PedidoCompraItem item : itens) {
            BigDecimal custo = precosCusto.get(item.getProdutoId());
            if (custo == null || custo.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal limite = custo.multiply(Constants.PEDIDO_COMPRA_TOLERANCIA_PRECO_ALERTA);
            if (item.getPrecoUnitario().compareTo(limite) > 0) {
                log.warn("Preço unitário {} do produto {} está acima da tolerância de 30% sobre o custo de "
                                + "referência {} (fornecedorId={})",
                        item.getPrecoUnitario(), item.getProdutoId(), custo, fornecedorId);
            }
        }
    }

    private void recalcularValores(PedidoCompra pedido, List<PedidoCompraItem> itens) {
        BigDecimal totalItens = BigDecimal.ZERO;
        for (PedidoCompraItem item : itens) {
            item.setValorTotal(item.getQuantidade().multiply(item.getPrecoUnitario()));
            totalItens = totalItens.add(item.getValorTotal());
        }
        BigDecimal frete = pedido.getValorFrete() != null ? pedido.getValorFrete() : BigDecimal.ZERO;
        pedido.setValorFrete(frete);
        pedido.setValorTotal(totalItens.add(frete));
    }

    private PedidoCompra buscarPedido(UUID pedidoId, Long tenantId) {
        return pedidoCompraRepository.findByIdAndTenantId(pedidoId, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.PEDIDO_COMPRA_NOT_FOUND, HttpStatus.BAD_REQUEST));
    }

    private void validarTransicao(StatusPedidoCompra origem, StatusPedidoCompra destino) {
        if (!TRANSICOES_VALIDAS.getOrDefault(origem, Set.of()).contains(destino)) {
            throw new BusinessException(
                    String.format(Constants.PEDIDO_COMPRA_TRANSICAO_INVALIDA, origem, destino), HttpStatus.BAD_REQUEST);
        }
    }

    private void registrarHistorico(PedidoCompra pedido, StatusPedidoCompra statusAnterior,
                                     StatusPedidoCompra statusNovo, String motivo, UUID userId, Instant agora) {
        CompraStatusHistorico historico = CompraStatusHistorico.builder()
                .documentoTipo(TipoDocumentoCompra.PEDIDO)
                .documentoId(pedido.getId())
                .statusAnterior(statusAnterior != null ? statusAnterior.name() : null)
                .statusNovo(statusNovo.name())
                .usuarioId(userId)
                .motivo(motivo)
                .ocorridoEm(agora)
                .build();
        historico.setTenantId(pedido.getTenantId());
        compraStatusHistoricoRepository.save(historico);
    }
}
