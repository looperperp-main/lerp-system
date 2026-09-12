package com.l.erp.operacoesservice.services.compras;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraFornecedorItemResponseDTO;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraFornecedorResponseDTO;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraRespostaItemRequestDTO;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraRespostaRequestDTO;
import com.l.erp.operacoesservice.domain.compras.CompraStatusHistorico;
import com.l.erp.operacoesservice.domain.compras.CotacaoCompra;
import com.l.erp.operacoesservice.domain.compras.CotacaoCompraFornecedor;
import com.l.erp.operacoesservice.domain.compras.CotacaoCompraFornecedorItem;
import com.l.erp.operacoesservice.domain.compras.CotacaoCompraItem;
import com.l.erp.operacoesservice.domain.compras.PedidoCompra;
import com.l.erp.operacoesservice.domain.compras.PedidoCompraItem;
import com.l.erp.operacoesservice.domain.compras.RequisicaoCompra;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusCotacaoCompra;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusCotacaoCompraFornecedor;
import com.l.erp.operacoesservice.domain.compras.enumerators.TipoDocumentoCompra;
import com.l.erp.operacoesservice.infra.client.CadastroServiceClient;
import com.l.erp.operacoesservice.repository.compras.CompraStatusHistoricoRepository;
import com.l.erp.operacoesservice.repository.compras.CotacaoCompraFornecedorItemRepository;
import com.l.erp.operacoesservice.repository.compras.CotacaoCompraFornecedorRepository;
import com.l.erp.operacoesservice.repository.compras.CotacaoCompraItemRepository;
import com.l.erp.operacoesservice.repository.compras.CotacaoCompraRepository;
import com.l.erp.operacoesservice.repository.compras.RequisicaoCompraItemRepository;
import com.l.erp.operacoesservice.repository.compras.RequisicaoCompraRepository;
import com.l.erp.operacoesservice.services.vendas.PedidoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cotação de compra multi-fornecedor: ABERTA → ENCERRADA/CANCELADA (spec/p2p-compras.md, Fase 5).
 * Mesmo padrão de PedidoCompraService/RequisicaoCompraService (Fase 1b/2). Convite/resposta de
 * cada fornecedor tem sub-status próprio (AGUARDANDO/RESPONDIDA/DECLINADA). O encerramento gera o
 * PedidoCompra vencedor via PedidoCompraService — a escolha do vencedor é sempre manual do
 * usuário; a ordenação sugerida (ver montarFornecedoresResponse) é só apoio à decisão.
 */
@Service
public class CotacaoCompraService {

    private static final Logger log = LoggerFactory.getLogger(CotacaoCompraService.class);

    private static final Map<StatusCotacaoCompra, Set<StatusCotacaoCompra>> TRANSICOES_VALIDAS = Map.of(
            StatusCotacaoCompra.ABERTA, Set.of(StatusCotacaoCompra.ENCERRADA, StatusCotacaoCompra.CANCELADA),
            StatusCotacaoCompra.ENCERRADA, Set.of(),
            StatusCotacaoCompra.CANCELADA, Set.of()
    );

    private static final Map<StatusCotacaoCompraFornecedor, Set<StatusCotacaoCompraFornecedor>> TRANSICOES_VALIDAS_FORNECEDOR =
            Map.of(
                    StatusCotacaoCompraFornecedor.AGUARDANDO,
                    Set.of(StatusCotacaoCompraFornecedor.RESPONDIDA, StatusCotacaoCompraFornecedor.DECLINADA),
                    StatusCotacaoCompraFornecedor.RESPONDIDA, Set.of(),
                    StatusCotacaoCompraFornecedor.DECLINADA, Set.of()
            );

    private final CotacaoCompraRepository cotacaoCompraRepository;
    private final CotacaoCompraItemRepository cotacaoCompraItemRepository;
    private final CotacaoCompraFornecedorRepository cotacaoCompraFornecedorRepository;
    private final CotacaoCompraFornecedorItemRepository cotacaoCompraFornecedorItemRepository;
    private final RequisicaoCompraRepository requisicaoCompraRepository;
    private final RequisicaoCompraItemRepository requisicaoCompraItemRepository;
    private final CompraStatusHistoricoRepository compraStatusHistoricoRepository;
    private final CompraNumeroService compraNumeroService;
    private final CadastroServiceClient cadastroServiceClient;
    private final RequisicaoCompraService requisicaoCompraService;
    private final PedidoCompraService pedidoCompraService;

    public CotacaoCompraService(CotacaoCompraRepository cotacaoCompraRepository,
                                 CotacaoCompraItemRepository cotacaoCompraItemRepository,
                                 CotacaoCompraFornecedorRepository cotacaoCompraFornecedorRepository,
                                 CotacaoCompraFornecedorItemRepository cotacaoCompraFornecedorItemRepository,
                                 RequisicaoCompraRepository requisicaoCompraRepository,
                                 RequisicaoCompraItemRepository requisicaoCompraItemRepository,
                                 CompraStatusHistoricoRepository compraStatusHistoricoRepository,
                                 CompraNumeroService compraNumeroService,
                                 CadastroServiceClient cadastroServiceClient,
                                 RequisicaoCompraService requisicaoCompraService,
                                 PedidoCompraService pedidoCompraService) {
        this.cotacaoCompraRepository = cotacaoCompraRepository;
        this.cotacaoCompraItemRepository = cotacaoCompraItemRepository;
        this.cotacaoCompraFornecedorRepository = cotacaoCompraFornecedorRepository;
        this.cotacaoCompraFornecedorItemRepository = cotacaoCompraFornecedorItemRepository;
        this.requisicaoCompraRepository = requisicaoCompraRepository;
        this.requisicaoCompraItemRepository = requisicaoCompraItemRepository;
        this.compraStatusHistoricoRepository = compraStatusHistoricoRepository;
        this.compraNumeroService = compraNumeroService;
        this.cadastroServiceClient = cadastroServiceClient;
        this.requisicaoCompraService = requisicaoCompraService;
        this.pedidoCompraService = pedidoCompraService;
    }

    // ---------------------------------------------------------------- criação

    @Transactional
    public CotacaoCompra criar(CotacaoCompra cotacao, List<CotacaoCompraItem> itensAvulsos, List<UUID> fornecedorIds,
                                Long tenantId, UUID userId) {
        RequisicaoCompra requisicao = null;
        List<CotacaoCompraItem> itens;
        if (cotacao.getRequisicaoId() != null) {
            requisicao = requisicaoCompraRepository.findByIdAndTenantId(cotacao.getRequisicaoId(), tenantId)
                    .orElseThrow(() -> new BusinessException(Constants.REQUISICAO_COMPRA_NOT_FOUND, HttpStatus.BAD_REQUEST));
            itens = requisicaoCompraItemRepository.findAllByRequisicaoId(requisicao.getId()).stream()
                    .map(ri -> CotacaoCompraItem.builder().produtoId(ri.getProdutoId()).quantidade(ri.getQuantidade()).build())
                    .toList();
        } else {
            itens = itensAvulsos;
        }
        validarItens(itens);
        if (fornecedorIds == null || fornecedorIds.isEmpty()) {
            throw new BusinessException(Constants.COTACAO_COMPRA_SEM_FORNECEDORES, HttpStatus.BAD_REQUEST);
        }
        List<UUID> fornecedoresUnicos = fornecedorIds.stream().distinct().toList();
        for (UUID fornecedorId : fornecedoresUnicos) {
            validarFornecedorAtivo(fornecedorId, tenantId, userId);
        }

        Instant agora = Instant.now();
        cotacao.setTenantId(tenantId);
        cotacao.setNumero(compraNumeroService.proximoNumero(tenantId, TipoDocumentoCompra.COTACAO));
        cotacao.setStatus(StatusCotacaoCompra.ABERTA);
        cotacao.setCreatedAt(agora);
        cotacao.setCreatedBy(userId);
        CotacaoCompra salva = cotacaoCompraRepository.save(cotacao);

        for (CotacaoCompraItem item : itens) {
            item.setCotacao(salva);
            item.setTenantId(tenantId);
            item.setCreatedAt(agora);
            item.setCreatedBy(userId);
        }
        cotacaoCompraItemRepository.saveAll(itens);

        for (UUID fornecedorId : fornecedoresUnicos) {
            CotacaoCompraFornecedor convite = CotacaoCompraFornecedor.builder()
                    .cotacao(salva)
                    .fornecedorId(fornecedorId)
                    .status(StatusCotacaoCompraFornecedor.AGUARDANDO)
                    .valorFrete(BigDecimal.ZERO)
                    .createdAt(agora)
                    .createdBy(userId)
                    .build();
            convite.setTenantId(tenantId);
            cotacaoCompraFornecedorRepository.save(convite);
        }

        registrarHistorico(salva, null, StatusCotacaoCompra.ABERTA, null, userId, agora);
        if (requisicao != null) {
            requisicaoCompraService.iniciarCotacao(requisicao.getId(), tenantId, userId);
        }
        return salva;
    }

    // ---------------------------------------------------------------- resposta/declínio do fornecedor

    @Transactional
    public CotacaoCompra registrarResposta(UUID cotacaoId, UUID cotacaoFornecedorId, Long tenantId, UUID userId,
                                            CotacaoCompraRespostaRequestDTO dto) {
        CotacaoCompra cotacao = buscarCotacao(cotacaoId, tenantId);
        validarCotacaoAberta(cotacao);
        CotacaoCompraFornecedor fornecedor = buscarFornecedorConvite(cotacaoFornecedorId, cotacaoId, tenantId);
        validarTransicaoFornecedor(fornecedor.getStatus(), StatusCotacaoCompraFornecedor.RESPONDIDA);
        if (dto.condicaoPagamentoId() == null) {
            throw new BusinessException(Constants.COTACAO_COMPRA_CONDICAO_PAGAMENTO_OBRIGATORIA, HttpStatus.BAD_REQUEST);
        }

        List<CotacaoCompraItem> itensCotacao = cotacaoCompraItemRepository.findAllByCotacaoId(cotacaoId);
        Set<UUID> idsCotacao = itensCotacao.stream().map(CotacaoCompraItem::getId).collect(java.util.stream.Collectors.toSet());
        Set<UUID> idsResposta = dto.itens().stream()
                .map(CotacaoCompraRespostaItemRequestDTO::cotacaoItemId).collect(java.util.stream.Collectors.toSet());
        if (!idsResposta.equals(idsCotacao)) {
            throw new BusinessException(Constants.COTACAO_COMPRA_RESPOSTA_ITENS_INCOMPLETA, HttpStatus.BAD_REQUEST);
        }

        Instant agora = Instant.now();
        cotacaoCompraFornecedorItemRepository.deleteAllByCotacaoFornecedorId(fornecedor.getId());
        List<CotacaoCompraFornecedorItem> itensResposta = dto.itens().stream()
                .map(itemDto -> {
                    CotacaoCompraFornecedorItem item = CotacaoCompraFornecedorItem.builder()
                            .cotacaoFornecedor(fornecedor)
                            .cotacaoItem(itensCotacao.stream()
                                    .filter(i -> i.getId().equals(itemDto.cotacaoItemId())).findFirst().orElseThrow())
                            .precoUnitario(itemDto.precoUnitario())
                            .createdAt(agora)
                            .createdBy(userId)
                            .build();
                    item.setTenantId(tenantId);
                    return item;
                })
                .toList();
        cotacaoCompraFornecedorItemRepository.saveAll(itensResposta);

        fornecedor.setCondicaoPagamentoId(dto.condicaoPagamentoId());
        fornecedor.setPrazoEntregaDias(dto.prazoEntregaDias());
        fornecedor.setValorFrete(dto.valorFrete() != null ? dto.valorFrete() : BigDecimal.ZERO);
        fornecedor.setObservacao(dto.observacao());
        fornecedor.setStatus(StatusCotacaoCompraFornecedor.RESPONDIDA);
        fornecedor.setUpdatedAt(agora);
        fornecedor.setLastUpdatedBy(userId);
        cotacaoCompraFornecedorRepository.save(fornecedor);
        return cotacao;
    }

    @Transactional
    public CotacaoCompra declinar(UUID cotacaoId, UUID cotacaoFornecedorId, Long tenantId, UUID userId, String motivo) {
        CotacaoCompra cotacao = buscarCotacao(cotacaoId, tenantId);
        validarCotacaoAberta(cotacao);
        CotacaoCompraFornecedor fornecedor = buscarFornecedorConvite(cotacaoFornecedorId, cotacaoId, tenantId);
        validarTransicaoFornecedor(fornecedor.getStatus(), StatusCotacaoCompraFornecedor.DECLINADA);

        fornecedor.setStatus(StatusCotacaoCompraFornecedor.DECLINADA);
        fornecedor.setObservacao(motivo);
        fornecedor.setUpdatedAt(Instant.now());
        fornecedor.setLastUpdatedBy(userId);
        cotacaoCompraFornecedorRepository.save(fornecedor);
        return cotacao;
    }

    // ---------------------------------------------------------------- transições da cotação

    @Transactional
    public CotacaoCompra encerrar(UUID cotacaoId, UUID cotacaoFornecedorVencedorId, Long tenantId, UUID userId) {
        CotacaoCompra cotacao = buscarCotacao(cotacaoId, tenantId);
        validarTransicaoCotacao(cotacao.getStatus(), StatusCotacaoCompra.ENCERRADA);
        CotacaoCompraFornecedor vencedor = buscarFornecedorConvite(cotacaoFornecedorVencedorId, cotacaoId, tenantId);
        if (vencedor.getStatus() != StatusCotacaoCompraFornecedor.RESPONDIDA) {
            throw new BusinessException(Constants.COTACAO_COMPRA_VENCEDOR_OBRIGATORIO, HttpStatus.BAD_REQUEST);
        }
        List<CotacaoCompraFornecedorItem> itensVencedor =
                cotacaoCompraFornecedorItemRepository.findAllByCotacaoFornecedorId(vencedor.getId());

        PedidoCompra rascunho = PedidoCompra.builder()
                .fornecedorId(vencedor.getFornecedorId())
                .condicaoPagamentoId(vencedor.getCondicaoPagamentoId())
                .depositoId(cotacao.getDepositoId())
                .requisicaoId(cotacao.getRequisicaoId())
                .dataPrevisaoEntrega(vencedor.getPrazoEntregaDias() != null
                        ? LocalDate.now().plusDays(vencedor.getPrazoEntregaDias()) : null)
                .valorFrete(vencedor.getValorFrete())
                .observacao("Gerado a partir da cotação nº " + cotacao.getNumero())
                .build();
        List<PedidoCompraItem> itensPedido = itensVencedor.stream()
                .map(fi -> PedidoCompraItem.builder()
                        .produtoId(fi.getCotacaoItem().getProdutoId())
                        .quantidade(fi.getCotacaoItem().getQuantidade())
                        .precoUnitario(fi.getPrecoUnitario())
                        .build())
                .toList();
        PedidoCompra pedidoGerado = pedidoCompraService.criar(rascunho, itensPedido, tenantId, userId);
        pedidoCompraService.vincularCotacaoFornecedor(pedidoGerado.getId(), vencedor.getId(), tenantId);

        Instant agora = Instant.now();
        StatusCotacaoCompra statusAnterior = cotacao.getStatus();
        cotacao.setStatus(StatusCotacaoCompra.ENCERRADA);
        cotacao.setCotacaoFornecedorVencedorId(vencedor.getId());
        cotacao.setUpdatedAt(agora);
        cotacao.setLastUpdatedBy(userId);
        cotacaoCompraRepository.save(cotacao);
        registrarHistorico(cotacao, statusAnterior, StatusCotacaoCompra.ENCERRADA, null, userId, agora);

        if (cotacao.getRequisicaoId() != null) {
            requisicaoCompraService.atender(cotacao.getRequisicaoId(), tenantId, userId);
        }
        return cotacao;
    }

    @Transactional
    public CotacaoCompra cancelar(UUID cotacaoId, Long tenantId, UUID userId, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException(Constants.COTACAO_COMPRA_MOTIVO_CANCELAMENTO_OBRIGATORIO, HttpStatus.BAD_REQUEST);
        }
        CotacaoCompra cotacao = buscarCotacao(cotacaoId, tenantId);
        validarTransicaoCotacao(cotacao.getStatus(), StatusCotacaoCompra.CANCELADA);

        Instant agora = Instant.now();
        StatusCotacaoCompra statusAnterior = cotacao.getStatus();
        cotacao.setStatus(StatusCotacaoCompra.CANCELADA);
        cotacao.setUpdatedAt(agora);
        cotacao.setLastUpdatedBy(userId);
        cotacaoCompraRepository.save(cotacao);
        registrarHistorico(cotacao, statusAnterior, StatusCotacaoCompra.CANCELADA, motivo, userId, agora);

        if (cotacao.getRequisicaoId() != null) {
            requisicaoCompraService.voltarParaAprovada(cotacao.getRequisicaoId(), tenantId, userId);
        }
        return cotacao;
    }

    // ---------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public CotacaoCompra buscarPorId(UUID cotacaoId, Long tenantId) {
        return buscarCotacao(cotacaoId, tenantId);
    }

    @Transactional(readOnly = true)
    public Page<CotacaoCompra> listar(Long tenantId, StatusCotacaoCompra status, UUID requisicaoId, Pageable pageable) {
        return cotacaoCompraRepository.buscarComFiltros(tenantId, status, requisicaoId, pageable);
    }

    @Transactional(readOnly = true)
    public List<CotacaoCompraItem> listarItens(UUID cotacaoId) {
        return cotacaoCompraItemRepository.findAllByCotacaoId(cotacaoId);
    }

    @Transactional(readOnly = true)
    public List<CotacaoCompraFornecedorResponseDTO> listarFornecedores(UUID cotacaoId, Long tenantId, UUID userId) {
        return montarFornecedoresResponse(cotacaoId, tenantId, userId);
    }

    @Transactional(readOnly = true)
    public List<CompraStatusHistorico> listarHistorico(UUID cotacaoId) {
        return compraStatusHistoricoRepository.findAllByDocumentoTipoAndDocumentoIdOrderByOcorridoEmAsc(
                TipoDocumentoCompra.COTACAO, cotacaoId);
    }

    // ---------------------------------------------------------------- ranking (apoio à decisão)

    // Critério de desempate (spec/p2p-compras.md, Fase 5): 1º menor preço líquido total
    // (itens+frete), 2º menor prazo de entrega, 3º melhor condição de pagamento (maior prazo médio
    // ponderado), 4º resposta mais recente. É só sugestão de ordenação — a escolha do vencedor é
    // sempre manual (EncerrarCotacaoRequestDTO).
    // ponytail: o 4º critério do spec ("persistindo empate, maior prazo de validade da proposta")
    // não tem campo modelado no schema — pulado direto pro critério de resposta mais recente;
    // adicionar prazoValidade em cotacao_compra_fornecedor se isso virar bloqueador real.
    private List<CotacaoCompraFornecedorResponseDTO> montarFornecedoresResponse(UUID cotacaoId, Long tenantId, UUID userId) {
        List<CotacaoCompraFornecedor> fornecedores = cotacaoCompraFornecedorRepository.findAllByCotacaoId(cotacaoId);
        Map<UUID, List<CotacaoCompraFornecedorItemResponseDTO>> itensPorFornecedor = new HashMap<>();
        Map<UUID, BigDecimal> valorTotalPorFornecedor = new HashMap<>();

        for (CotacaoCompraFornecedor f : fornecedores) {
            List<CotacaoCompraFornecedorItemResponseDTO> itensDto = cotacaoCompraFornecedorItemRepository
                    .findAllByCotacaoFornecedorId(f.getId()).stream()
                    .map(fi -> new CotacaoCompraFornecedorItemResponseDTO(
                            fi.getCotacaoItem().getId(), fi.getCotacaoItem().getProdutoId(), fi.getCotacaoItem().getQuantidade(),
                            fi.getPrecoUnitario(), fi.getPrecoUnitario().multiply(fi.getCotacaoItem().getQuantidade())))
                    .toList();
            itensPorFornecedor.put(f.getId(), itensDto);
            BigDecimal valorItens = itensDto.stream()
                    .map(CotacaoCompraFornecedorItemResponseDTO::valorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            valorTotalPorFornecedor.put(f.getId(), valorItens.add(f.getValorFrete()));
        }

        List<CotacaoCompraFornecedor> respondidas = fornecedores.stream()
                .filter(f -> f.getStatus() == StatusCotacaoCompraFornecedor.RESPONDIDA)
                .sorted(Comparator
                        .comparing((CotacaoCompraFornecedor f) -> valorTotalPorFornecedor.get(f.getId()))
                        .thenComparing(f -> f.getPrazoEntregaDias() == null ? Integer.MAX_VALUE : f.getPrazoEntregaDias())
                        .thenComparing(f -> prazoMedioPonderado(f.getCondicaoPagamentoId(), tenantId, userId),
                                Comparator.reverseOrder())
                        .thenComparing(CotacaoCompraFornecedor::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        Map<UUID, Integer> ordemPorFornecedor = new HashMap<>();
        for (int i = 0; i < respondidas.size(); i++) {
            ordemPorFornecedor.put(respondidas.get(i).getId(), i + 1);
        }

        return fornecedores.stream()
                .map(f -> new CotacaoCompraFornecedorResponseDTO(
                        f.getId(), f.getFornecedorId(), f.getStatus(), f.getCondicaoPagamentoId(), f.getPrazoEntregaDias(),
                        f.getValorFrete(), f.getObservacao(),
                        f.getStatus() == StatusCotacaoCompraFornecedor.RESPONDIDA ? valorTotalPorFornecedor.get(f.getId()) : null,
                        ordemPorFornecedor.get(f.getId()), itensPorFornecedor.get(f.getId())))
                .toList();
    }

    // RN-P2P-03 (3º critério do desempate): prazo médio ponderado pelas parcelas da condição de
    // pagamento — quanto maior, melhor. Best-effort: se o cadastro-service não achar a condição
    // (diferente dos outros métodos de CadastroServiceClient, buscarParcelas lança exceção em vez
    // de devolver fallback), o fornecedor entra com peso neutro nesse critério, sem travar o ranking.
    private BigDecimal prazoMedioPonderado(UUID condicaoPagamentoId, Long tenantId, UUID userId) {
        try {
            List<PedidoService.ParcelaDefinicao> parcelas = cadastroServiceClient.buscarParcelas(condicaoPagamentoId, tenantId, userId);
            BigDecimal totalPercentual = BigDecimal.ZERO;
            BigDecimal somaPonderada = BigDecimal.ZERO;
            for (PedidoService.ParcelaDefinicao parcela : parcelas) {
                BigDecimal percentual = parcela.percentual() != null ? parcela.percentual() : BigDecimal.ZERO;
                BigDecimal dias = BigDecimal.valueOf(parcela.diasPrazo() != null ? parcela.diasPrazo() : 0);
                somaPonderada = somaPonderada.add(percentual.multiply(dias));
                totalPercentual = totalPercentual.add(percentual);
            }
            return totalPercentual.compareTo(BigDecimal.ZERO) > 0
                    ? somaPonderada.divide(totalPercentual, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        } catch (BusinessException e) {
            log.warn("Condição de pagamento {} não encontrada no cadastro-service ao calcular desempate da cotação", condicaoPagamentoId);
            return BigDecimal.ZERO;
        }
    }

    // ---------------------------------------------------------------- helpers

    private void validarItens(List<CotacaoCompraItem> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new BusinessException(Constants.COTACAO_COMPRA_SEM_ITENS, HttpStatus.BAD_REQUEST);
        }
    }

    // RN-P2P-02: fornecedor precisa estar ativo pra ser convidado.
    private void validarFornecedorAtivo(UUID fornecedorId, Long tenantId, UUID userId) {
        CadastroServiceClient.FornecedorRef fornecedor = cadastroServiceClient.buscarFornecedor(fornecedorId, tenantId, userId);
        if (Boolean.FALSE.equals(fornecedor.ativo())) {
            throw new BusinessException(
                    String.format(Constants.COTACAO_COMPRA_FORNECEDOR_INATIVO, fornecedor.pessoaNomeRazao()),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private CotacaoCompra buscarCotacao(UUID cotacaoId, Long tenantId) {
        return cotacaoCompraRepository.findByIdAndTenantId(cotacaoId, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.COTACAO_COMPRA_NOT_FOUND, HttpStatus.BAD_REQUEST));
    }

    private CotacaoCompraFornecedor buscarFornecedorConvite(UUID cotacaoFornecedorId, UUID cotacaoId, Long tenantId) {
        CotacaoCompraFornecedor fornecedor = cotacaoCompraFornecedorRepository.findByIdAndTenantId(cotacaoFornecedorId, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.COTACAO_COMPRA_FORNECEDOR_NOT_FOUND, HttpStatus.BAD_REQUEST));
        if (!fornecedor.getCotacao().getId().equals(cotacaoId)) {
            throw new BusinessException(Constants.COTACAO_COMPRA_FORNECEDOR_NOT_FOUND, HttpStatus.BAD_REQUEST);
        }
        return fornecedor;
    }

    // Resposta/declínio de convite só valem com a cotação ainda ABERTA.
    private void validarCotacaoAberta(CotacaoCompra cotacao) {
        if (cotacao.getStatus() != StatusCotacaoCompra.ABERTA) {
            throw new BusinessException(
                    String.format(Constants.COTACAO_COMPRA_TRANSICAO_INVALIDA, cotacao.getStatus(), StatusCotacaoCompra.ABERTA),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void validarTransicaoCotacao(StatusCotacaoCompra origem, StatusCotacaoCompra destino) {
        if (!TRANSICOES_VALIDAS.getOrDefault(origem, Set.of()).contains(destino)) {
            throw new BusinessException(
                    String.format(Constants.COTACAO_COMPRA_TRANSICAO_INVALIDA, origem, destino), HttpStatus.BAD_REQUEST);
        }
    }

    private void validarTransicaoFornecedor(StatusCotacaoCompraFornecedor origem, StatusCotacaoCompraFornecedor destino) {
        if (!TRANSICOES_VALIDAS_FORNECEDOR.getOrDefault(origem, Set.of()).contains(destino)) {
            throw new BusinessException(
                    String.format(Constants.COTACAO_COMPRA_TRANSICAO_INVALIDA, origem, destino), HttpStatus.BAD_REQUEST);
        }
    }

    private void registrarHistorico(CotacaoCompra cotacao, StatusCotacaoCompra statusAnterior, StatusCotacaoCompra statusNovo,
                                     String motivo, UUID userId, Instant agora) {
        CompraStatusHistorico historico = CompraStatusHistorico.builder()
                .documentoTipo(TipoDocumentoCompra.COTACAO)
                .documentoId(cotacao.getId())
                .statusAnterior(statusAnterior != null ? statusAnterior.name() : null)
                .statusNovo(statusNovo.name())
                .usuarioId(userId)
                .motivo(motivo)
                .ocorridoEm(agora)
                .build();
        historico.setTenantId(cotacao.getTenantId());
        compraStatusHistoricoRepository.save(historico);
    }
}
