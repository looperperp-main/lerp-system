package com.l.erp.operacoesservice.services.compras;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.api.dto.RecebimentoMercadoriaItemRequestDTO;
import com.l.erp.operacoesservice.domain.compras.CompraStatusHistorico;
import com.l.erp.operacoesservice.domain.compras.PedidoCompra;
import com.l.erp.operacoesservice.domain.compras.PedidoCompraItem;
import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoria;
import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoriaItem;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusPedidoCompra;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusRecebimentoMercadoria;
import com.l.erp.operacoesservice.domain.compras.enumerators.TipoDocumentoCompra;
import com.l.erp.operacoesservice.domain.compras.enumerators.TipoDocumentoFiscal;
import com.l.erp.operacoesservice.domain.estoque.enumerators.OrigemMovimentoEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.TipoMovimentoEstoque;
import com.l.erp.operacoesservice.infra.client.CadastroServiceClient;
import com.l.erp.operacoesservice.repository.compras.CompraStatusHistoricoRepository;
import com.l.erp.operacoesservice.repository.compras.PedidoCompraItemRepository;
import com.l.erp.operacoesservice.repository.compras.RecebimentoMercadoriaItemRepository;
import com.l.erp.operacoesservice.repository.compras.RecebimentoMercadoriaRepository;
import com.l.erp.operacoesservice.services.estoque.EstoqueService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Recebimento de mercadoria (spec/p2p-compras.md, Fase 3): cria em EM_CONFERENCIA, confirma
 * (RN-P2P-05, baixa de estoque in-process, recalcula status do pedido) e cancela (espelho do
 * confirmar quando já CONFIRMADO). Mesmo padrão de camadas de PedidoCompraService (Fase 2).
 */
@Service
public class RecebimentoMercadoriaService {

    private final RecebimentoMercadoriaRepository recebimentoMercadoriaRepository;
    private final RecebimentoMercadoriaItemRepository recebimentoMercadoriaItemRepository;
    private final PedidoCompraItemRepository pedidoCompraItemRepository;
    private final CompraStatusHistoricoRepository compraStatusHistoricoRepository;
    private final CompraNumeroService compraNumeroService;
    private final PedidoCompraService pedidoCompraService;
    private final EstoqueService estoqueService;
    private final CadastroServiceClient cadastroServiceClient;
    private final ApplicationEventPublisher eventPublisher;

    public RecebimentoMercadoriaService(RecebimentoMercadoriaRepository recebimentoMercadoriaRepository,
                                         RecebimentoMercadoriaItemRepository recebimentoMercadoriaItemRepository,
                                         PedidoCompraItemRepository pedidoCompraItemRepository,
                                         CompraStatusHistoricoRepository compraStatusHistoricoRepository,
                                         CompraNumeroService compraNumeroService,
                                         PedidoCompraService pedidoCompraService,
                                         EstoqueService estoqueService,
                                         CadastroServiceClient cadastroServiceClient,
                                         ApplicationEventPublisher eventPublisher) {
        this.recebimentoMercadoriaRepository = recebimentoMercadoriaRepository;
        this.recebimentoMercadoriaItemRepository = recebimentoMercadoriaItemRepository;
        this.pedidoCompraItemRepository = pedidoCompraItemRepository;
        this.compraStatusHistoricoRepository = compraStatusHistoricoRepository;
        this.compraNumeroService = compraNumeroService;
        this.pedidoCompraService = pedidoCompraService;
        this.estoqueService = estoqueService;
        this.cadastroServiceClient = cadastroServiceClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public RecebimentoMercadoria criar(UUID pedidoId, RecebimentoMercadoria dados,
                                        List<RecebimentoMercadoriaItemRequestDTO> itensDto,
                                        Long tenantId, UUID userId) {
        PedidoCompra pedido = pedidoCompraService.buscarPorId(pedidoId, tenantId);
        validarStatusPedidoParaRecebimento(pedido);
        validarDadosFiscais(dados);

        ItensResolvidos resolvidos = resolverItens(pedido, itensDto, tenantId, userId);

        Long numero = compraNumeroService.proximoNumero(tenantId, TipoDocumentoCompra.RECEBIMENTO);
        Instant agora = Instant.now();

        dados.setPedido(pedido);
        dados.setNumero(numero);
        dados.setStatus(StatusRecebimentoMercadoria.EM_CONFERENCIA);
        dados.setDepositoId(resolverDeposito(dados.getDepositoId(), pedido, resolvidos.temItemMercadoria()));
        dados.setCondicaoPagamentoId(dados.getCondicaoPagamentoId() != null
                ? dados.getCondicaoPagamentoId() : pedido.getCondicaoPagamentoId());
        dados.setImpostosIbs(ou0(dados.getImpostosIbs()));
        dados.setImpostosCbs(ou0(dados.getImpostosCbs()));
        dados.setImpostosIs(ou0(dados.getImpostosIs()));
        dados.setTenantId(tenantId);
        dados.setCreatedAt(agora);
        dados.setCreatedBy(userId);

        RecebimentoMercadoria salvo = recebimentoMercadoriaRepository.save(dados);
        salvarItens(salvo, resolvidos.itens(), tenantId, userId, agora);
        registrarHistorico(salvo, null, StatusRecebimentoMercadoria.EM_CONFERENCIA, null, userId, agora);
        return salvo;
    }

    @Transactional
    public RecebimentoMercadoria atualizar(UUID recebimentoId, Long tenantId, UUID userId,
                                            RecebimentoMercadoria dados,
                                            List<RecebimentoMercadoriaItemRequestDTO> itensDto) {
        RecebimentoMercadoria existente = buscarRecebimento(recebimentoId, tenantId);
        if (existente.getStatus() != StatusRecebimentoMercadoria.EM_CONFERENCIA) {
            throw new BusinessException(Constants.RECEBIMENTO_COMPRA_UPDATE_SO_EM_CONFERENCIA, HttpStatus.BAD_REQUEST);
        }
        validarDadosFiscais(dados);
        ItensResolvidos resolvidos = resolverItens(existente.getPedido(), itensDto, tenantId, userId);

        existente.setDepositoId(resolverDeposito(dados.getDepositoId(), existente.getPedido(), resolvidos.temItemMercadoria()));
        existente.setDataRecebimento(dados.getDataRecebimento());
        existente.setTipoDocumentoFiscal(dados.getTipoDocumentoFiscal());
        existente.setNfeNumero(dados.getNfeNumero());
        existente.setNfeSerie(dados.getNfeSerie());
        existente.setNfeChave(dados.getNfeChave());
        existente.setNfseCodigoVerificacao(dados.getNfseCodigoVerificacao());
        existente.setNfeDataEmissao(dados.getNfeDataEmissao());
        existente.setValorTotalNf(dados.getValorTotalNf());
        existente.setCondicaoPagamentoId(dados.getCondicaoPagamentoId() != null
                ? dados.getCondicaoPagamentoId() : existente.getPedido().getCondicaoPagamentoId());
        existente.setObservacao(dados.getObservacao());
        existente.setUpdatedAt(Instant.now());
        existente.setLastUpdatedBy(userId);
        recebimentoMercadoriaRepository.save(existente);

        recebimentoMercadoriaItemRepository.deleteAllByRecebimentoId(existente.getId());
        salvarItens(existente, resolvidos.itens(), tenantId, userId, Instant.now());
        return existente;
    }

    @Transactional
    public RecebimentoMercadoria confirmar(UUID recebimentoId, Long tenantId, UUID userId) {
        RecebimentoMercadoria recebimento = buscarRecebimento(recebimentoId, tenantId);
        validarTransicao(recebimento.getStatus(), StatusRecebimentoMercadoria.CONFIRMADO);
        PedidoCompra pedido = recebimento.getPedido();
        validarStatusPedidoParaRecebimento(pedido);

        List<RecebimentoMercadoriaItem> itens = recebimentoMercadoriaItemRepository.findAllByRecebimentoId(recebimento.getId());
        List<EstoqueService.MovimentoRequisicao.Linha> linhasMercadoria = new ArrayList<>();
        boolean temItemMercadoria = false;

        for (RecebimentoMercadoriaItem item : itens) {
            PedidoCompraItem pedidoItem = item.getPedidoItem();
            CadastroServiceClient.ProdutoRef produto =
                    cadastroServiceClient.buscarProduto(pedidoItem.getProdutoId(), tenantId, userId);
            boolean mercadoria = !"SERVICO".equals(produto.tipo());

            BigDecimal limite = pedidoItem.getQuantidade().multiply(Constants.RECEBIMENTO_COMPRA_TOLERANCIA_QUANTIDADE);
            BigDecimal acumulado = pedidoItem.getQuantidadeRecebida().add(item.getQuantidade());
            if (acumulado.compareTo(limite) > 0) {
                throw new BusinessException(String.format(Constants.RECEBIMENTO_COMPRA_QUANTIDADE_EXCEDE_TOLERANCIA,
                        pedidoItem.getProdutoId(), acumulado, limite), HttpStatus.BAD_REQUEST);
            }
            pedidoItem.setQuantidadeRecebida(acumulado);
            pedidoCompraItemRepository.save(pedidoItem);

            if (mercadoria) {
                temItemMercadoria = true;
                linhasMercadoria.add(new EstoqueService.MovimentoRequisicao.Linha(
                        pedidoItem.getProdutoId(), item.getQuantidade(), item.getPrecoUnitarioNf()));
            }
        }

        if (temItemMercadoria && recebimento.getDepositoId() == null) {
            throw new BusinessException(Constants.RECEBIMENTO_COMPRA_DEPOSITO_OBRIGATORIO, HttpStatus.BAD_REQUEST);
        }

        Instant agora = Instant.now();
        if (!linhasMercadoria.isEmpty()) {
            estoqueService.registrarMovimento(new EstoqueService.MovimentoRequisicao(tenantId, userId,
                    TipoMovimentoEstoque.ENTRADA_COMPRA, OrigemMovimentoEstoque.RECEBIMENTO, recebimento.getId(),
                    recebimento.getDepositoId(), agora, null, linhasMercadoria));
        }

        pedidoCompraService.recalcularStatusAposRecebimento(pedido.getId(), tenantId, userId);

        recebimento.setStatus(StatusRecebimentoMercadoria.CONFIRMADO);
        recebimento.setUpdatedAt(agora);
        recebimento.setLastUpdatedBy(userId);
        recebimentoMercadoriaRepository.save(recebimento);
        registrarHistorico(recebimento, StatusRecebimentoMercadoria.EM_CONFERENCIA,
                StatusRecebimentoMercadoria.CONFIRMADO, null, userId, agora);

        eventPublisher.publishEvent(new RecebimentoConfirmadoEvent(recebimento, pedido, itens));
        return recebimento;
    }

    @Transactional
    public RecebimentoMercadoria cancelar(UUID recebimentoId, Long tenantId, UUID userId, String motivo) {
        RecebimentoMercadoria recebimento = buscarRecebimento(recebimentoId, tenantId);
        StatusRecebimentoMercadoria statusAnterior = recebimento.getStatus();
        validarTransicao(statusAnterior, StatusRecebimentoMercadoria.CANCELADO);
        PedidoCompra pedido = recebimento.getPedido();

        if (statusAnterior == StatusRecebimentoMercadoria.CONFIRMADO) {
            estornarConfirmacao(recebimento, pedido, tenantId, userId);
        }

        Instant agora = Instant.now();
        recebimento.setStatus(StatusRecebimentoMercadoria.CANCELADO);
        recebimento.setMotivoCancelamento(motivo);
        recebimento.setUpdatedAt(agora);
        recebimento.setLastUpdatedBy(userId);
        recebimentoMercadoriaRepository.save(recebimento);
        registrarHistorico(recebimento, statusAnterior, StatusRecebimentoMercadoria.CANCELADO, motivo, userId, agora);

        if (statusAnterior == StatusRecebimentoMercadoria.CONFIRMADO) {
            eventPublisher.publishEvent(new RecebimentoCanceladoEvent(recebimento, pedido));
        }
        return recebimento;
    }

    // Espelho de confirmar(): devolve quantidade_recebida por item e estorna o estoque (RN, mesma
    // transação) — spec/p2p-compras.md §"Integração com estoque".
    private void estornarConfirmacao(RecebimentoMercadoria recebimento, PedidoCompra pedido, Long tenantId, UUID userId) {
        List<RecebimentoMercadoriaItem> itens = recebimentoMercadoriaItemRepository.findAllByRecebimentoId(recebimento.getId());
        List<EstoqueService.MovimentoRequisicao.Linha> linhasMercadoria = new ArrayList<>();

        for (RecebimentoMercadoriaItem item : itens) {
            PedidoCompraItem pedidoItem = item.getPedidoItem();
            pedidoItem.setQuantidadeRecebida(pedidoItem.getQuantidadeRecebida().subtract(item.getQuantidade()));
            pedidoCompraItemRepository.save(pedidoItem);

            CadastroServiceClient.ProdutoRef produto =
                    cadastroServiceClient.buscarProduto(pedidoItem.getProdutoId(), tenantId, userId);
            if (!"SERVICO".equals(produto.tipo())) {
                linhasMercadoria.add(new EstoqueService.MovimentoRequisicao.Linha(
                        pedidoItem.getProdutoId(), item.getQuantidade(), item.getPrecoUnitarioNf()));
            }
        }

        if (!linhasMercadoria.isEmpty()) {
            estoqueService.registrarMovimento(new EstoqueService.MovimentoRequisicao(tenantId, userId,
                    TipoMovimentoEstoque.ESTORNO_ENTRADA_COMPRA, OrigemMovimentoEstoque.RECEBIMENTO, recebimento.getId(),
                    recebimento.getDepositoId(), Instant.now(), null, linhasMercadoria));
        }

        pedidoCompraService.recalcularStatusAposRecebimento(pedido.getId(), tenantId, userId);
    }

    @Transactional(readOnly = true)
    public RecebimentoMercadoria buscarPorId(UUID recebimentoId, Long tenantId) {
        return buscarRecebimento(recebimentoId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<RecebimentoMercadoriaItem> listarItens(UUID recebimentoId) {
        return recebimentoMercadoriaItemRepository.findAllByRecebimentoId(recebimentoId);
    }

    @Transactional(readOnly = true)
    public Page<RecebimentoMercadoria> listar(Long tenantId, StatusRecebimentoMercadoria status, UUID pedidoId,
                                               LocalDate dataRecebimentoDe, LocalDate dataRecebimentoAte,
                                               Pageable pageable) {
        return recebimentoMercadoriaRepository.buscarComFiltros(tenantId, status, pedidoId,
                dataRecebimentoDe, dataRecebimentoAte, pageable);
    }

    private void validarStatusPedidoParaRecebimento(PedidoCompra pedido) {
        if (pedido.getStatus() != StatusPedidoCompra.ENVIADO && pedido.getStatus() != StatusPedidoCompra.RECEBIDO_PARCIAL) {
            throw new BusinessException(Constants.RECEBIMENTO_COMPRA_PEDIDO_STATUS_INVALIDO, HttpStatus.BAD_REQUEST);
        }
    }

    // RN-P2P-07: nfeSerie/nfeChave obrigatórios se NFE; nfseCodigoVerificacao obrigatório se NFSE.
    private void validarDadosFiscais(RecebimentoMercadoria dados) {
        boolean valido = dados.getTipoDocumentoFiscal() == TipoDocumentoFiscal.NFE
                ? isNotBlank(dados.getNfeSerie()) && isNotBlank(dados.getNfeChave())
                : isNotBlank(dados.getNfseCodigoVerificacao());
        if (!valido) {
            throw new BusinessException(
                    String.format(Constants.RECEBIMENTO_COMPRA_DADOS_FISCAIS_INCONSISTENTES, dados.getTipoDocumentoFiscal()),
                    HttpStatus.BAD_REQUEST);
        }
    }

    // Resolve cada item contra o PedidoCompraItem referenciado (RN-P2P-08: precisa pertencer ao
    // mesmo pedido) e o tipo do produto no cadastro-service (RN-P2P-11: item de serviço exige
    // codigoServico; item de mercadoria conta pra depositoId obrigatório).
    private ItensResolvidos resolverItens(PedidoCompra pedido, List<RecebimentoMercadoriaItemRequestDTO> itensDto,
                                           Long tenantId, UUID userId) {
        if (itensDto == null || itensDto.isEmpty()) {
            throw new BusinessException(Constants.RECEBIMENTO_COMPRA_SEM_ITENS, HttpStatus.BAD_REQUEST);
        }
        boolean temItemMercadoria = false;
        List<RecebimentoMercadoriaItem> itens = new ArrayList<>();
        for (RecebimentoMercadoriaItemRequestDTO itemDto : itensDto) {
            PedidoCompraItem pedidoItem = pedidoCompraItemRepository.findById(itemDto.pedidoItemId())
                    .filter(pi -> pi.getPedido().getId().equals(pedido.getId()))
                    .orElseThrow(() -> new BusinessException(
                            Constants.RECEBIMENTO_COMPRA_ITEM_PEDIDO_INVALIDO, HttpStatus.BAD_REQUEST));

            CadastroServiceClient.ProdutoRef produto =
                    cadastroServiceClient.buscarProduto(pedidoItem.getProdutoId(), tenantId, userId);
            if ("SERVICO".equals(produto.tipo())) {
                if (produto.codigoServico() == null || produto.codigoServico().isBlank()) {
                    throw new BusinessException(
                            String.format(Constants.RECEBIMENTO_COMPRA_ITEM_SERVICO_SEM_CODIGO, produto.nome()),
                            HttpStatus.BAD_REQUEST);
                }
            } else {
                temItemMercadoria = true;
            }

            itens.add(RecebimentoMercadoriaItem.builder()
                    .pedidoItem(pedidoItem)
                    .quantidade(itemDto.quantidade())
                    .precoUnitarioNf(itemDto.precoUnitarioNf())
                    .build());
        }
        return new ItensResolvidos(itens, temItemMercadoria);
    }

    // Default = depósito do pedido, editável (Rev. 5) — só exigido quando há item de mercadoria;
    // recebimento só-serviço fica sem depósito se nenhum for informado.
    private UUID resolverDeposito(UUID depositoInformado, PedidoCompra pedido, boolean temItemMercadoria) {
        if (depositoInformado != null) {
            return depositoInformado;
        }
        return temItemMercadoria ? pedido.getDepositoId() : null;
    }

    private void salvarItens(RecebimentoMercadoria recebimento, List<RecebimentoMercadoriaItem> itens,
                              Long tenantId, UUID userId, Instant agora) {
        for (RecebimentoMercadoriaItem item : itens) {
            item.setRecebimento(recebimento);
            item.setTenantId(tenantId);
            item.setCreatedAt(agora);
            item.setCreatedBy(userId);
        }
        recebimentoMercadoriaItemRepository.saveAll(itens);
    }

    private RecebimentoMercadoria buscarRecebimento(UUID recebimentoId, Long tenantId) {
        return recebimentoMercadoriaRepository.findByIdAndTenantId(recebimentoId, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.RECEBIMENTO_COMPRA_NOT_FOUND, HttpStatus.BAD_REQUEST));
    }

    private void validarTransicao(StatusRecebimentoMercadoria origem, StatusRecebimentoMercadoria destino) {
        boolean valida = switch (destino) {
            case CONFIRMADO -> origem == StatusRecebimentoMercadoria.EM_CONFERENCIA;
            case CANCELADO -> origem == StatusRecebimentoMercadoria.EM_CONFERENCIA
                    || origem == StatusRecebimentoMercadoria.CONFIRMADO;
            default -> false; // FATURADO é Fase 4 — nenhuma transição pra lá nesta fase.
        };
        if (!valida) {
            throw new BusinessException(
                    String.format(Constants.RECEBIMENTO_COMPRA_TRANSICAO_INVALIDA, origem, destino), HttpStatus.BAD_REQUEST);
        }
    }

    private void registrarHistorico(RecebimentoMercadoria recebimento, StatusRecebimentoMercadoria statusAnterior,
                                     StatusRecebimentoMercadoria statusNovo, String motivo, UUID userId, Instant agora) {
        CompraStatusHistorico historico = CompraStatusHistorico.builder()
                .documentoTipo(TipoDocumentoCompra.RECEBIMENTO)
                .documentoId(recebimento.getId())
                .statusAnterior(statusAnterior != null ? statusAnterior.name() : null)
                .statusNovo(statusNovo.name())
                .usuarioId(userId)
                .motivo(motivo)
                .ocorridoEm(agora)
                .build();
        historico.setTenantId(recebimento.getTenantId());
        compraStatusHistoricoRepository.save(historico);
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static BigDecimal ou0(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private record ItensResolvidos(List<RecebimentoMercadoriaItem> itens, boolean temItemMercadoria) {
    }
}
