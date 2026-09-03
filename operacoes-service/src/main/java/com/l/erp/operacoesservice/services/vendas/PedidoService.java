package com.l.erp.operacoesservice.services.vendas;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.domain.vendas.Pedido;
import com.l.erp.operacoesservice.domain.vendas.PedidoItem;
import com.l.erp.operacoesservice.domain.vendas.PedidoStatusHistorico;
import com.l.erp.operacoesservice.domain.vendas.enumerators.ModalidadeFrete;
import com.l.erp.operacoesservice.domain.vendas.enumerators.StatusPedido;
import com.l.erp.operacoesservice.repository.vendas.PedidoItemRepository;
import com.l.erp.operacoesservice.repository.vendas.PedidoRepository;
import com.l.erp.operacoesservice.repository.vendas.PedidoStatusHistoricoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CRUD do orçamento, máquina de estados e validações de negócio do O2C (spec/o2c-vendas.md §4/§7/§8, Fase 3).
 *
 * <p>ponytail: dois pontos de integração externa ainda não existem nesta fase e ficam stub/parametrizados —
 * o motor de preço (§6: "até lá, stub que força preco_manual" — item sem {@code precoUnitario} informado
 * vira erro de "sem preço vigente", equivalente ao 404 real do resolver) e a validação em lote de
 * referências no cadastro-service (§2: endpoint {@code /interno/referencias/validar} ainda não existe,
 * pendência registrada na Fase 0 do spec). Dados que viriam do cadastro-service por HTTP (limite de
 * crédito do cliente, parcelas da condição de pagamento) entram como parâmetro — resolvidos pelo
 * chamador quando o client HTTP for escrito (Fase 4/controller).</p>
 */
@Service
public class PedidoService {

    private static final Map<StatusPedido, Set<StatusPedido>> TRANSICOES_VALIDAS = Map.of(
            StatusPedido.ORCAMENTO, Set.of(StatusPedido.CONFIRMADO, StatusPedido.BLOQUEADO_CREDITO, StatusPedido.CANCELADO),
            StatusPedido.BLOQUEADO_CREDITO, Set.of(StatusPedido.CONFIRMADO, StatusPedido.ORCAMENTO, StatusPedido.CANCELADO),
            StatusPedido.CONFIRMADO, Set.of(StatusPedido.EXPEDIDO, StatusPedido.CANCELADO),
            StatusPedido.EXPEDIDO, Set.of(StatusPedido.FATURADO, StatusPedido.CANCELADO),
            StatusPedido.FATURADO, Set.of(),
            StatusPedido.CANCELADO, Set.of()
    );

    private static final Set<StatusPedido> STATUS_EXPOSICAO_CREDITO =
            Set.of(StatusPedido.CONFIRMADO, StatusPedido.EXPEDIDO);

    private final PedidoRepository pedidoRepository;
    private final PedidoItemRepository pedidoItemRepository;
    private final PedidoStatusHistoricoRepository pedidoStatusHistoricoRepository;
    private final PedidoNumeroService pedidoNumeroService;

    public PedidoService(PedidoRepository pedidoRepository,
                          PedidoItemRepository pedidoItemRepository,
                          PedidoStatusHistoricoRepository pedidoStatusHistoricoRepository,
                          PedidoNumeroService pedidoNumeroService) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoItemRepository = pedidoItemRepository;
        this.pedidoStatusHistoricoRepository = pedidoStatusHistoricoRepository;
        this.pedidoNumeroService = pedidoNumeroService;
    }

    // ---------------------------------------------------------------- criação do orçamento (§7)

    @Transactional
    public Pedido criarOrcamento(Pedido pedido, List<PedidoItem> itens, Long tenantId, UUID userId) {
        if (itens == null || itens.isEmpty()) {
            throw new BusinessException(Constants.PEDIDO_SEM_ITENS, HttpStatus.BAD_REQUEST);
        }
        aplicarCabecalhoPadrao(pedido);

        validarItensSemDuplicidade(itens);
        Instant agora = Instant.now();
        for (PedidoItem item : itens) {
            resolverPrecoEValidarItem(item, tenantId, userId, agora);
        }

        pedido.setTenantId(tenantId);
        pedido.setNumero(pedidoNumeroService.proximoNumero(tenantId));
        pedido.setStatus(StatusPedido.ORCAMENTO);
        pedido.setCreatedAt(agora);
        pedido.setCreatedBy(userId);
        recalcularTotaisDosItens(pedido, itens);

        Pedido salvo = pedidoRepository.save(pedido);
        for (PedidoItem item : itens) {
            item.setPedido(salvo);
        }
        pedidoItemRepository.saveAll(itens);

        registrarHistorico(salvo, null, StatusPedido.ORCAMENTO, null, userId, agora);
        return salvo;
    }

    private void validarItensSemDuplicidade(List<PedidoItem> itens) {
        Set<UUID> produtos = new HashSet<>();
        for (PedidoItem item : itens) {
            if (!produtos.add(item.getProdutoId())) {
                throw new BusinessException(
                        String.format(Constants.PEDIDO_ITEM_PRODUTO_DUPLICADO, item.getProdutoId()),
                        HttpStatus.BAD_REQUEST);
            }
        }
    }

    /**
     * ponytail: stub do resolver de preço (§6) — sem client HTTP pro cadastro-service ainda, item
     * sem {@code precoUnitario} informado é tratado como "sem preço vigente" (mesmo efeito de um 404
     * real do resolver). Quando a fase 3 do motor existir, este método passa a chamá-lo antes de cair
     * nesse erro; snapshot de tabela/origem fica {@code null} até lá — não há de onde vir.
     */
    private void resolverPrecoEValidarItem(PedidoItem item, Long tenantId, UUID userId, Instant agora) {
        if (item.getQuantidade() == null || item.getQuantidade().signum() <= 0) {
            throw new BusinessException(Constants.PEDIDO_ITEM_QUANTIDADE_INVALIDA, HttpStatus.BAD_REQUEST);
        }
        if (item.getPrecoUnitario() == null) {
            throw new BusinessException(
                    String.format(Constants.PEDIDO_ITEM_SEM_PRECO, item.getProdutoId()), HttpStatus.BAD_REQUEST);
        }
        item.setPrecoManual(true);

        BigDecimal desconto = item.getDesconto() != null ? item.getDesconto() : BigDecimal.ZERO;
        BigDecimal bruto = item.getQuantidade().multiply(item.getPrecoUnitario());
        if (desconto.signum() < 0 || desconto.compareTo(bruto) >= 0) {
            throw new BusinessException(Constants.PEDIDO_ITEM_DESCONTO_INVALIDO, HttpStatus.BAD_REQUEST);
        }
        item.setDesconto(desconto);
        item.setValorTotal(bruto.subtract(desconto));
        item.setTenantId(tenantId);
        item.setCreatedAt(agora);
        item.setCreatedBy(userId);
    }

    private void recalcularTotaisDosItens(Pedido pedido, List<PedidoItem> itens) {
        BigDecimal valorItens = BigDecimal.ZERO;
        BigDecimal valorDesconto = BigDecimal.ZERO;
        for (PedidoItem item : itens) {
            valorItens = valorItens.add(item.getQuantidade().multiply(item.getPrecoUnitario()));
            valorDesconto = valorDesconto.add(item.getDesconto());
        }
        pedido.setValorItens(valorItens);
        pedido.setValorDesconto(valorDesconto);
        recalcularValorTotal(pedido);
    }

    /** valor_total = valor_itens - valor_desconto + valor_frete (spec §3.1). */
    private void recalcularValorTotal(Pedido pedido) {
        BigDecimal valorFrete = pedido.getValorFrete() != null ? pedido.getValorFrete() : BigDecimal.ZERO;
        pedido.setValorTotal(pedido.getValorItens().subtract(pedido.getValorDesconto()).add(valorFrete));
    }

    // ---------------------------------------------------------------- transições de estado (§4)

    @Transactional
    public Pedido confirmar(UUID pedidoId, Long tenantId, UUID userId, boolean temPermissaoSemLimite,
                             BigDecimal limiteCredito) {
        Pedido pedido = buscarPedido(pedidoId, tenantId);
        StatusPedido statusAtual = pedido.getStatus();
        if (statusAtual != StatusPedido.ORCAMENTO && statusAtual != StatusPedido.BLOQUEADO_CREDITO) {
            throw transicaoInvalida(statusAtual, StatusPedido.CONFIRMADO);
        }
        if (pedido.getCondicaoPagamentoId() == null) {
            throw new BusinessException(Constants.PEDIDO_CONDICAO_PAGAMENTO_OBRIGATORIA, HttpStatus.BAD_REQUEST);
        }
        if (pedido.getDataValidade() != null && pedido.getDataValidade().isBefore(LocalDate.now())) {
            throw new BusinessException(
                    String.format(Constants.PEDIDO_ORCAMENTO_EXPIRADO, pedido.getDataValidade()),
                    HttpStatus.BAD_REQUEST);
        }

        // ponytail: exposição soma só pedidos locais (CONFIRMADO/EXPEDIDO ainda não faturados); o AR
        // do financeiro-service (§7) entra na soma quando esse serviço existir — hoje não existe no
        // monorepo. limiteCredito vem por parâmetro (Cliente.limiteCredito, via API — Fase 4).
        BigDecimal exposicaoLocal = pedidoRepository.somaValorTotalPorStatus(
                tenantId, pedido.getClienteId(), STATUS_EXPOSICAO_CREDITO, pedido.getId());
        BigDecimal exposicao = pedido.getValorTotal().add(exposicaoLocal);
        boolean estourouLimite = limiteCredito != null && exposicao.compareTo(limiteCredito) > 0;

        Instant agora = Instant.now();
        if (estourouLimite && !temPermissaoSemLimite) {
            pedido.setStatus(StatusPedido.BLOQUEADO_CREDITO);
            pedido.setUpdatedAt(agora);
            pedido.setLastUpdatedBy(userId);
            pedidoRepository.save(pedido);
            registrarHistorico(pedido, statusAtual, StatusPedido.BLOQUEADO_CREDITO,
                    String.format(Constants.PEDIDO_BLOQUEADO_CREDITO_MOTIVO, exposicao, limiteCredito),
                    userId, agora);
            return pedido;
        }

        String motivo = estourouLimite
                ? String.format(Constants.PEDIDO_CONFIRMADO_COM_BYPASS_MOTIVO, exposicao, limiteCredito)
                : null;
        pedido.setStatus(StatusPedido.CONFIRMADO);
        pedido.setDataConfirmacao(agora);
        pedido.setUpdatedAt(agora);
        pedido.setLastUpdatedBy(userId);
        pedidoRepository.save(pedido);
        registrarHistorico(pedido, statusAtual, StatusPedido.CONFIRMADO, motivo, userId, agora);
        return pedido;
    }

    @Transactional
    public Pedido reabrir(UUID pedidoId, Long tenantId, UUID userId) {
        Pedido pedido = buscarPedido(pedidoId, tenantId);
        validarTransicao(pedido.getStatus(), StatusPedido.ORCAMENTO);

        StatusPedido statusAnterior = pedido.getStatus();
        Instant agora = Instant.now();
        pedido.setStatus(StatusPedido.ORCAMENTO);
        pedido.setUpdatedAt(agora);
        pedido.setLastUpdatedBy(userId);
        pedidoRepository.save(pedido);
        registrarHistorico(pedido, statusAnterior, StatusPedido.ORCAMENTO, null, userId, agora);
        return pedido;
    }

    @Transactional
    public Pedido expedir(UUID pedidoId, Long tenantId, UUID userId, UUID depositoId, UUID transportadoraId,
                           BigDecimal valorFrete, ModalidadeFrete modalidadeFrete) {
        Pedido pedido = buscarPedido(pedidoId, tenantId);
        validarTransicao(pedido.getStatus(), StatusPedido.EXPEDIDO);
        if (depositoId == null) {
            throw new BusinessException(Constants.PEDIDO_DEPOSITO_OBRIGATORIO, HttpStatus.BAD_REQUEST);
        }
        ModalidadeFrete modalidade = modalidadeFrete != null ? modalidadeFrete : pedido.getModalidadeFrete();
        if (modalidade != ModalidadeFrete.SEM_FRETE && transportadoraId == null) {
            throw new BusinessException(Constants.PEDIDO_TRANSPORTADORA_OBRIGATORIA, HttpStatus.BAD_REQUEST);
        }

        StatusPedido statusAnterior = pedido.getStatus();
        Instant agora = Instant.now();
        pedido.setDepositoId(depositoId);
        pedido.setTransportadoraId(transportadoraId);
        pedido.setModalidadeFrete(modalidade);
        pedido.setValorFrete(valorFrete);
        recalcularValorTotal(pedido);
        pedido.setStatus(StatusPedido.EXPEDIDO);
        pedido.setDataExpedicao(agora);
        pedido.setUpdatedAt(agora);
        pedido.setLastUpdatedBy(userId);
        pedidoRepository.save(pedido);
        registrarHistorico(pedido, statusAnterior, StatusPedido.EXPEDIDO, null, userId, agora);
        // ponytail: baixa de estoque in-process (SAIDA_VENDA), mesma transação — pendente do módulo
        // de estoque, ainda não escrito neste serviço (spec §7-expedição). Liga aqui quando existir.
        return pedido;
    }

    @Transactional
    public FaturamentoResultado faturar(UUID pedidoId, Long tenantId, UUID userId,
                                         List<ParcelaDefinicao> parcelasDefinicao) {
        Pedido pedido = buscarPedido(pedidoId, tenantId);
        validarTransicao(pedido.getStatus(), StatusPedido.FATURADO);

        BigDecimal somaPercentuais = parcelasDefinicao.stream()
                .map(ParcelaDefinicao::percentual)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (somaPercentuais.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new BusinessException(
                    String.format(Constants.PEDIDO_PARCELAS_PERCENTUAL_INVALIDO, somaPercentuais),
                    HttpStatus.BAD_REQUEST);
        }

        StatusPedido statusAnterior = pedido.getStatus();
        Instant agora = Instant.now();
        LocalDate dataFaturamento = LocalDate.now();
        pedido.setStatus(StatusPedido.FATURADO);
        pedido.setDataFaturamento(agora);
        pedido.setUpdatedAt(agora);
        pedido.setLastUpdatedBy(userId);
        pedidoRepository.save(pedido);
        registrarHistorico(pedido, statusAnterior, StatusPedido.FATURADO, null, userId, agora);

        List<ParcelaFaturamento> parcelas = calcularParcelas(pedido.getValorTotal(), dataFaturamento, parcelasDefinicao);
        // ponytail: publicação do evento venda.pedido.faturado (AFTER_COMMIT) é Fase 5 — aqui só
        // devolve pedido + parcelas pro chamador (futuro controller/producer) montar o payload (§8).
        return new FaturamentoResultado(pedido, parcelas);
    }

    @Transactional
    public Pedido cancelar(UUID pedidoId, Long tenantId, UUID userId, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException(Constants.PEDIDO_MOTIVO_CANCELAMENTO_OBRIGATORIO, HttpStatus.BAD_REQUEST);
        }
        Pedido pedido = buscarPedido(pedidoId, tenantId);
        validarTransicao(pedido.getStatus(), StatusPedido.CANCELADO);

        StatusPedido statusAnterior = pedido.getStatus();
        Instant agora = Instant.now();
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setDataCancelamento(agora);
        pedido.setMotivoCancelamento(motivo);
        pedido.setUpdatedAt(agora);
        pedido.setLastUpdatedBy(userId);
        pedidoRepository.save(pedido);
        registrarHistorico(pedido, statusAnterior, StatusPedido.CANCELADO, motivo, userId, agora);
        // ponytail: estorno de estoque (ESTORNO_SAIDA_VENDA) quando statusAnterior == EXPEDIDO fica
        // pendente do módulo de estoque, mesma ressalva do expedir() acima (spec §7-cancelamento).
        return pedido;
    }

    // ---------------------------------------------------------------- API/controllers (Fase 4, §5/§10)

    @Transactional
    public Pedido atualizar(UUID pedidoId, Long tenantId, UUID userId, Pedido dados, List<PedidoItem> itens) {
        Pedido pedido = buscarPedido(pedidoId, tenantId);
        if (pedido.getStatus() != StatusPedido.ORCAMENTO) {
            throw new BusinessException(Constants.PEDIDO_UPDATE_SO_ORCAMENTO, HttpStatus.BAD_REQUEST);
        }
        if (itens.isEmpty()) {
            throw new BusinessException(Constants.PEDIDO_SEM_ITENS, HttpStatus.BAD_REQUEST);
        }

        pedido.setClienteId(dados.getClienteId());
        pedido.setVendedorId(dados.getVendedorId());
        pedido.setCondicaoPagamentoId(dados.getCondicaoPagamentoId());
        pedido.setDataEmissao(dados.getDataEmissao());
        pedido.setDataValidade(dados.getDataValidade());
        pedido.setModalidadeFrete(dados.getModalidadeFrete());
        pedido.setObservacao(dados.getObservacao());
        aplicarCabecalhoPadrao(pedido);

        validarItensSemDuplicidade(itens);
        Instant agora = Instant.now();
        for (PedidoItem item : itens) {
            resolverPrecoEValidarItem(item, tenantId, userId, agora);
        }

        pedidoItemRepository.deleteAllByPedidoId(pedidoId);
        for (PedidoItem item : itens) {
            item.setPedido(pedido);
        }
        pedidoItemRepository.saveAll(itens);
        recalcularTotaisDosItens(pedido, itens);
        pedido.setUpdatedAt(agora);
        pedido.setLastUpdatedBy(userId);
        return pedidoRepository.save(pedido);
    }

    @Transactional
    public Pedido recalcularPrecos(UUID pedidoId, Long tenantId) {
        Pedido pedido = buscarPedido(pedidoId, tenantId);
        if (pedido.getStatus() != StatusPedido.ORCAMENTO) {
            throw new BusinessException(Constants.PEDIDO_RECALCULO_SO_ORCAMENTO, HttpStatus.BAD_REQUEST);
        }
        // ponytail: motor de preço ainda não existe (§6) — resolverPrecoEValidarItem sempre marca
        // precoManual=true, então hoje nenhum item é elegível a recálculo automático. No-op até o
        // resolver existir; endpoint fica pronto pro contrato da API.
        return pedido;
    }

    @Transactional(readOnly = true)
    public Pedido buscarPorId(UUID pedidoId, Long tenantId) {
        return buscarPedido(pedidoId, tenantId);
    }

    @Transactional(readOnly = true)
    public Page<Pedido> listar(Long tenantId, StatusPedido status, UUID clienteId, UUID vendedorId, Long numero,
                                LocalDate dataEmissaoDe, LocalDate dataEmissaoAte, Pageable pageable) {
        return pedidoRepository.buscarComFiltros(
                tenantId, status, clienteId, vendedorId, numero, dataEmissaoDe, dataEmissaoAte, pageable);
    }

    @Transactional(readOnly = true)
    public List<PedidoItem> listarItens(UUID pedidoId) {
        return pedidoItemRepository.findAllByPedidoId(pedidoId);
    }

    @Transactional(readOnly = true)
    public List<PedidoStatusHistorico> listarHistorico(UUID pedidoId) {
        return pedidoStatusHistoricoRepository.findAllByPedidoIdOrderByCreatedAtAsc(pedidoId);
    }

    // ---------------------------------------------------------------- parcelas do faturamento (§8)

    /**
     * Arredonda cada parcela a 2 casas (HALF_UP); o resto do arredondamento vai inteiro na última
     * parcela — a soma das parcelas é sempre exatamente igual a {@code valorTotal}.
     */
    static List<ParcelaFaturamento> calcularParcelas(BigDecimal valorTotal, LocalDate dataFaturamento,
                                                       List<ParcelaDefinicao> definicoes) {
        List<ParcelaFaturamento> parcelas = new ArrayList<>();
        BigDecimal somaCalculada = BigDecimal.ZERO;
        for (int i = 0; i < definicoes.size(); i++) {
            ParcelaDefinicao def = definicoes.get(i);
            boolean ultima = i == definicoes.size() - 1;
            BigDecimal valor = ultima
                    ? valorTotal.subtract(somaCalculada).setScale(2, RoundingMode.HALF_UP)
                    : valorTotal.multiply(def.percentual()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (!ultima) {
                somaCalculada = somaCalculada.add(valor);
            }
            parcelas.add(new ParcelaFaturamento(def.numero(), dataFaturamento.plusDays(def.diasPrazo()), valor,
                    def.formaPagamento()));
        }
        return parcelas;
    }

    // ---------------------------------------------------------------- helpers

    /** Data de emissão default, validade x emissão e modalidade de frete default (§7). */
    private void aplicarCabecalhoPadrao(Pedido pedido) {
        if (pedido.getDataEmissao() == null) {
            pedido.setDataEmissao(LocalDate.now());
        }
        if (pedido.getDataValidade() != null && pedido.getDataValidade().isBefore(pedido.getDataEmissao())) {
            throw new BusinessException(Constants.PEDIDO_DATA_VALIDADE_INVALIDA, HttpStatus.BAD_REQUEST);
        }
        if (pedido.getModalidadeFrete() == null) {
            pedido.setModalidadeFrete(ModalidadeFrete.SEM_FRETE);
        }
    }

    private Pedido buscarPedido(UUID pedidoId, Long tenantId) {
        return pedidoRepository.findByIdAndTenantId(pedidoId, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.PEDIDO_NOT_FOUND, HttpStatus.BAD_REQUEST));
    }

    private void validarTransicao(StatusPedido origem, StatusPedido destino) {
        if (!TRANSICOES_VALIDAS.getOrDefault(origem, Set.of()).contains(destino)) {
            throw transicaoInvalida(origem, destino);
        }
    }

    private BusinessException transicaoInvalida(StatusPedido origem, StatusPedido destino) {
        return new BusinessException(
                String.format(Constants.PEDIDO_TRANSICAO_INVALIDA, origem, destino), HttpStatus.BAD_REQUEST);
    }

    private void registrarHistorico(Pedido pedido, StatusPedido statusDe, StatusPedido statusPara, String motivo,
                                     UUID userId, Instant agora) {
        PedidoStatusHistorico historico = PedidoStatusHistorico.builder()
                .pedido(pedido)
                .statusDe(statusDe)
                .statusPara(statusPara)
                .motivo(motivo)
                .createdAt(agora)
                .createdBy(userId)
                .build();
        historico.setTenantId(pedido.getTenantId());
        pedidoStatusHistoricoRepository.save(historico);
    }

    // ---------------------------------------------------------------- tipos auxiliares (§8)

    /** Parcela da condição de pagamento (CondicaoPagamentoParcela, obtida via API do cadastro-service). */
    public record ParcelaDefinicao(Integer numero, Integer diasPrazo, BigDecimal percentual, String formaPagamento) {
    }

    /** Parcela calculada no faturamento — base do payload do evento venda.pedido.faturado (§8, Fase 5). */
    public record ParcelaFaturamento(Integer numero, LocalDate dataVencimento, BigDecimal valor, String formaPagamento) {
    }

    public record FaturamentoResultado(Pedido pedido, List<ParcelaFaturamento> parcelas) {
    }
}
