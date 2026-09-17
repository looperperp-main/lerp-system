package com.l.erp.operacoesservice.services.estoque;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.domain.estoque.EstoqueSaldo;
import com.l.erp.operacoesservice.domain.estoque.FechamentoEstoque;
import com.l.erp.operacoesservice.domain.estoque.MovimentoEstoque;
import com.l.erp.operacoesservice.domain.estoque.PendenciaEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.OrigemMovimentoEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.PendenciaTipoEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.TipoAjusteEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.TipoMovimentoEstoque;
import com.l.erp.operacoesservice.infra.client.CadastroServiceClient;
import com.l.erp.operacoesservice.repository.estoque.EstoqueSaldoRepository;
import com.l.erp.operacoesservice.repository.estoque.FechamentoEstoqueRepository;
import com.l.erp.operacoesservice.repository.estoque.MovimentoEstoqueRepository;
import com.l.erp.operacoesservice.repository.estoque.PendenciaEstoqueRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Caminho único de escrita do estoque (spec/modulos/estoque/estoque.md §4) — expedição, cancelamento, recebimento,
 * ajuste e apontamento de produção passam todos por {@link #registrarMovimento}, na mesma transação do
 * chamador ({@code @Transactional} sem {@code REQUIRES_NEW}).
 */
@Service
public class EstoqueService {

    private static final Set<TipoMovimentoEstoque> TIPOS_ENTRADA = EnumSet.of(
            TipoMovimentoEstoque.ENTRADA_COMPRA, TipoMovimentoEstoque.ESTORNO_SAIDA_VENDA,
            TipoMovimentoEstoque.AJUSTE_ENTRADA, TipoMovimentoEstoque.ENTRADA_PRODUCAO);
    // RN-EST-12 [D10, §12]: sujeitos a bloqueio de saldo negativo por finalidade do produto — substitui a
    // flag única estoque.bloquear-saida. ESTORNO_* nunca é bloqueado (reverter um fato já registrado).
    private static final Set<TipoMovimentoEstoque> TIPOS_SUJEITOS_A_BLOQUEIO = EnumSet.of(
            TipoMovimentoEstoque.SAIDA_VENDA, TipoMovimentoEstoque.AJUSTE_SAIDA,
            TipoMovimentoEstoque.SAIDA_CONSUMO, TipoMovimentoEstoque.SAIDA_PRODUCAO);
    private static final Set<OrigemMovimentoEstoque> ORIGENS_SEM_DOCUMENTO = EnumSet.of(
            OrigemMovimentoEstoque.AJUSTE, OrigemMovimentoEstoque.INVENTARIO);
    // RN-EST-09 [D8, §12]: custo médio recalculado só em entradas com lastro de valor (ESTORNO_* fica de
    // fora — reverter não deve distorcer a média).
    private static final Set<TipoMovimentoEstoque> TIPOS_QUE_RECALCULAM_CUSTO_MEDIO = EnumSet.of(
            TipoMovimentoEstoque.ENTRADA_COMPRA, TipoMovimentoEstoque.AJUSTE_ENTRADA,
            TipoMovimentoEstoque.ENTRADA_PRODUCAO);
    private static final Set<TipoMovimentoEstoque> TIPOS_QUE_LEEM_CUSTO_MEDIO = EnumSet.of(
            TipoMovimentoEstoque.SAIDA_VENDA, TipoMovimentoEstoque.SAIDA_CONSUMO,
            TipoMovimentoEstoque.AJUSTE_SAIDA, TipoMovimentoEstoque.SAIDA_PRODUCAO);
    private static final String FINALIDADE_PRODUTO_ACABADO = "PRODUTO_ACABADO";

    private final MovimentoEstoqueRepository movimentoEstoqueRepository;
    private final EstoqueSaldoRepository estoqueSaldoRepository;
    private final PendenciaEstoqueRepository pendenciaEstoqueRepository;
    private final FechamentoEstoqueRepository fechamentoEstoqueRepository;
    private final CadastroServiceClient cadastroServiceClient;

    public EstoqueService(MovimentoEstoqueRepository movimentoEstoqueRepository,
                           EstoqueSaldoRepository estoqueSaldoRepository,
                           PendenciaEstoqueRepository pendenciaEstoqueRepository,
                           FechamentoEstoqueRepository fechamentoEstoqueRepository,
                           CadastroServiceClient cadastroServiceClient) {
        this.movimentoEstoqueRepository = movimentoEstoqueRepository;
        this.estoqueSaldoRepository = estoqueSaldoRepository;
        this.pendenciaEstoqueRepository = pendenciaEstoqueRepository;
        this.fechamentoEstoqueRepository = fechamentoEstoqueRepository;
        this.cadastroServiceClient = cadastroServiceClient;
    }

    /** API in-process (spec/modulos/estoque/estoque.md §2.2/§4.1). */
    @Transactional
    public void registrarMovimento(MovimentoRequisicao req) {
        validar(req);

        Map<UUID, List<MovimentoRequisicao.Linha>> porProduto = req.linhas().stream()
                .collect(Collectors.groupingBy(MovimentoRequisicao.Linha::produtoId));
        boolean entrada = TIPOS_ENTRADA.contains(req.tipo());

        // passo 3: ordem determinística de lock — evita deadlock entre transações concorrentes
        // que mexem nos mesmos produtos.
        for (UUID produtoId : porProduto.keySet().stream().sorted().toList()) {
            List<MovimentoRequisicao.Linha> linhasDoProduto = porProduto.get(produtoId);
            BigDecimal quantidadeTotal = linhasDoProduto.stream()
                    .map(MovimentoRequisicao.Linha::quantidade)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal valorInformado = mediaPonderada(linhasDoProduto, quantidadeTotal);

            // RN-EST-11 [D9, §12]: ajuste positivo sem lastro de valor não entra (SALDO_INICIAL é a
            // única exceção — carga inicial pode não ter custo apurado ainda).
            if (req.tipo() == TipoMovimentoEstoque.AJUSTE_ENTRADA
                    && req.tipoAjuste() != TipoAjusteEstoque.SALDO_INICIAL && valorInformado == null) {
                throw new BusinessException(Constants.ESTOQUE_AJUSTE_ENTRADA_SEM_CUSTO, HttpStatus.BAD_REQUEST);
            }

            EstoqueSaldo saldo = buscarOuCriarSaldo(req, produtoId);
            BigDecimal saldoAnterior = saldo.getQuantidade();
            BigDecimal saldoNovo = entrada ? saldoAnterior.add(quantidadeTotal) : saldoAnterior.subtract(quantidadeTotal);

            // RN-EST-12 [D10, §12]: bloqueio por finalidade do produto, não mais uma flag única — decide
            // se lança 400 (bloqueado) ou segue e marca pendência de regularização/apontar produção.
            PendenciaTipoEstoque tipoPendencia = null;
            if (TIPOS_SUJEITOS_A_BLOQUEIO.contains(req.tipo()) && saldoNovo.signum() < 0) {
                CadastroServiceClient.ProdutoRef produto = cadastroServiceClient.buscarProduto(produtoId, req.tenantId(), req.userId());
                boolean produtoAcabado = produto != null && FINALIDADE_PRODUTO_ACABADO.equals(produto.finalidade());
                if (!produtoAcabado && !req.permitirSaldoNegativo()) {
                    String nomeProduto = produto != null ? produto.nome() : produtoId.toString();
                    throw new BusinessException(String.format(Constants.ESTOQUE_SALDO_INSUFICIENTE,
                            nomeProduto, req.depositoId(), saldoAnterior, req.origemId()), HttpStatus.BAD_REQUEST);
                }
                tipoPendencia = produtoAcabado ? PendenciaTipoEstoque.APONTAR_PRODUCAO : PendenciaTipoEstoque.REGULARIZACAO;
            }

            // RN-EST-09 [D8, §12]: custo médio recalculado só em entrada com lastro (ESTORNO_* fica de
            // fora); saída sempre grava o custo médio vigente, nunca o valor informado na linha.
            BigDecimal valorUnitario = null;
            if (TIPOS_QUE_RECALCULAM_CUSTO_MEDIO.contains(req.tipo())) {
                saldo.setCustoMedio(recalcularCustoMedio(saldoAnterior, saldo.getCustoMedio(),
                        quantidadeTotal, valorInformado));
                valorUnitario = valorInformado;
            } else if (TIPOS_QUE_LEEM_CUSTO_MEDIO.contains(req.tipo())) {
                valorUnitario = saldo.getCustoMedio();
            }

            saldo.setQuantidade(saldoNovo);
            saldo.setUpdatedAt(req.ocorridoEm());
            saldo.setLastUpdatedBy(req.userId());
            estoqueSaldoRepository.save(saldo);

            MovimentoEstoque movimento = gravarMovimento(req, produtoId, quantidadeTotal, valorUnitario);
            if (tipoPendencia != null) {
                criarPendencia(req, produtoId, tipoPendencia, movimento.getId());
            }
        }
    }

    private void criarPendencia(MovimentoRequisicao req, UUID produtoId, PendenciaTipoEstoque tipo, UUID movimentoId) {
        PendenciaEstoque pendencia = PendenciaEstoque.builder()
                .produtoId(produtoId)
                .depositoId(req.depositoId())
                .tipo(tipo)
                .movimentoId(movimentoId)
                .resolvida(false)
                .criadaEm(req.ocorridoEm())
                .build();
        pendencia.setTenantId(req.tenantId());
        pendenciaEstoqueRepository.save(pendencia);
    }

    /** RN-EST-13 [D10, §12] — nunca fecha com pendência aberta ou saldo negativo em nenhum produto/depósito. */
    @Transactional
    public void fecharPeriodo(Long tenantId, String competencia, UUID userId) {
        if (competencia == null || !competencia.matches("\\d{4}-\\d{2}")) {
            throw new BusinessException(Constants.ESTOQUE_FECHAMENTO_COMPETENCIA_INVALIDA, HttpStatus.BAD_REQUEST);
        }
        if (fechamentoEstoqueRepository.existsByTenantIdAndCompetencia(tenantId, competencia)) {
            throw new BusinessException(Constants.ESTOQUE_FECHAMENTO_JA_REALIZADO, HttpStatus.CONFLICT);
        }
        if (pendenciaEstoqueRepository.existsByTenantIdAndResolvidaFalse(tenantId)) {
            throw new BusinessException(Constants.ESTOQUE_FECHAMENTO_PENDENCIA_ABERTA, HttpStatus.CONFLICT);
        }
        if (estoqueSaldoRepository.existsByTenantIdAndQuantidadeLessThan(tenantId, BigDecimal.ZERO)) {
            throw new BusinessException(Constants.ESTOQUE_FECHAMENTO_SALDO_NEGATIVO, HttpStatus.CONFLICT);
        }
        FechamentoEstoque fechamento = FechamentoEstoque.builder()
                .competencia(competencia)
                .dataFechamento(Instant.now())
                .usuarioId(userId)
                .build();
        fechamento.setTenantId(tenantId);
        fechamentoEstoqueRepository.save(fechamento);
    }

    public Page<PendenciaEstoque> buscarPendencias(Long tenantId, Boolean resolvida, Pageable pageable) {
        return pendenciaEstoqueRepository.buscarComFiltro(tenantId, resolvida, pageable);
    }

    @Transactional
    public void resolverPendencia(Long tenantId, UUID id, UUID userId) {
        PendenciaEstoque pendencia = pendenciaEstoqueRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.ESTOQUE_PENDENCIA_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (Boolean.TRUE.equals(pendencia.getResolvida())) {
            throw new BusinessException(Constants.ESTOQUE_PENDENCIA_JA_RESOLVIDA, HttpStatus.CONFLICT);
        }
        pendencia.setResolvida(true);
        pendencia.setResolvidaEm(Instant.now());
        pendencia.setResolvidoPor(userId);
        pendenciaEstoqueRepository.save(pendencia);
    }

    /**
     * Custo médio móvel (RN-EST-09). {@code null} enquanto o saldo nunca recebeu entrada com valor —
     * saldo negativo/zerado conta como base 0 pra não distorcer a ponderação.
     */
    private BigDecimal recalcularCustoMedio(BigDecimal saldoAnterior, BigDecimal custoAnterior,
                                             BigDecimal quantidadeEntrada, BigDecimal valorEntrada) {
        if (valorEntrada == null) {
            return custoAnterior;
        }
        BigDecimal saldoBase = saldoAnterior.signum() > 0 ? saldoAnterior : BigDecimal.ZERO;
        BigDecimal saldoTotal = saldoBase.add(quantidadeEntrada);
        if (saldoTotal.signum() <= 0) {
            return custoAnterior;
        }
        BigDecimal custoBase = custoAnterior != null ? custoAnterior : BigDecimal.ZERO;
        BigDecimal somaValor = saldoBase.multiply(custoBase).add(quantidadeEntrada.multiply(valorEntrada));
        return somaValor.divide(saldoTotal, 4, RoundingMode.HALF_UP);
    }

    /**
     * Ajuste/inventário por saldo contado (spec/modulos/estoque/estoque.md §5.3, D5): o operador informa o saldo
     * que de fato existe, não diferença — elimina erro de sinal invertido.
     */
    @Transactional
    public void ajustar(AjusteRequisicao req) {
        if (req.quantidadeContada() == null || req.quantidadeContada().signum() < 0) {
            throw new BusinessException(Constants.ESTOQUE_QUANTIDADE_CONTADA_INVALIDA, HttpStatus.BAD_REQUEST);
        }
        if (req.origem() != OrigemMovimentoEstoque.AJUSTE && req.origem() != OrigemMovimentoEstoque.INVENTARIO) {
            throw new BusinessException(Constants.ESTOQUE_ORIGEM_AJUSTE_INVALIDA, HttpStatus.BAD_REQUEST);
        }

        BigDecimal saldoAtual = estoqueSaldoRepository
                .findByProdutoIdAndDepositoIdForUpdate(req.tenantId(), req.produtoId(), req.depositoId())
                .map(EstoqueSaldo::getQuantidade)
                .orElse(BigDecimal.ZERO);
        BigDecimal delta = req.quantidadeContada().subtract(saldoAtual);
        if (delta.signum() == 0) {
            return; // contagem confirma o saldo — não é fato de estoque (§5.3)
        }

        TipoMovimentoEstoque tipo = delta.signum() > 0
                ? TipoMovimentoEstoque.AJUSTE_ENTRADA
                : TipoMovimentoEstoque.AJUSTE_SAIDA;
        Instant agora = Instant.now();
        registrarMovimento(new MovimentoRequisicao(req.tenantId(), req.userId(), tipo, req.origem(), null,
                req.depositoId(), agora, req.motivo(), null, req.tipoAjuste(), req.documentoReferencia(),
                List.of(new MovimentoRequisicao.Linha(req.produtoId(), delta.abs(), req.valorUnitario())),
                req.permitirSaldoNegativo()));
    }

    public Page<EstoqueSaldo> buscarSaldos(Long tenantId, UUID produtoId, UUID depositoId, boolean comSaldo,
                                            Pageable pageable) {
        return estoqueSaldoRepository.buscarComFiltros(tenantId, produtoId, depositoId, comSaldo, pageable);
    }

    public Page<MovimentoEstoque> buscarMovimentos(Long tenantId, UUID produtoId, UUID depositoId, Instant de,
                                                    Instant ate, TipoMovimentoEstoque tipo,
                                                    OrigemMovimentoEstoque origemTipo, Pageable pageable) {
        return movimentoEstoqueRepository.buscarComFiltros(tenantId, produtoId, depositoId, de, ate, tipo,
                origemTipo, pageable);
    }

    private void validar(MovimentoRequisicao req) {
        if (req.depositoId() == null) {
            throw new BusinessException(Constants.ESTOQUE_DEPOSITO_OBRIGATORIO, HttpStatus.BAD_REQUEST);
        }
        if (req.linhas() == null || req.linhas().isEmpty()) {
            throw new BusinessException(Constants.ESTOQUE_SEM_LINHAS, HttpStatus.BAD_REQUEST);
        }
        for (MovimentoRequisicao.Linha linha : req.linhas()) {
            if (linha.quantidade() == null || linha.quantidade().signum() <= 0) {
                throw new BusinessException(Constants.ESTOQUE_QUANTIDADE_INVALIDA, HttpStatus.BAD_REQUEST);
            }
        }
        if (ORIGENS_SEM_DOCUMENTO.contains(req.origemTipo())) {
            // RN-EST-11 [D9, §12]: tipo_ajuste obrigatório em AJUSTE/INVENTARIO.
            if (req.tipoAjuste() == null) {
                throw new BusinessException(Constants.ESTOQUE_TIPO_AJUSTE_OBRIGATORIO, HttpStatus.BAD_REQUEST);
            }
            if (req.motivo() != null && req.motivo().length() > 500) {
                throw new BusinessException(Constants.ESTOQUE_MOTIVO_TAMANHO_INVALIDO, HttpStatus.BAD_REQUEST);
            }
        }
        // origem_id nulo (§3.2): requisição de almoxarifado (CONSUMO) e ajuste/inventário não têm documento de origem.
        if (req.origemId() == null && req.origemTipo() != OrigemMovimentoEstoque.CONSUMO
                && !ORIGENS_SEM_DOCUMENTO.contains(req.origemTipo())) {
            throw new BusinessException(Constants.ESTOQUE_ORIGEM_ID_OBRIGATORIO, HttpStatus.BAD_REQUEST);
        }
        // RN-EST-10 [D7, §12]: sem centro de custo não há contrapartida contábil.
        if (req.tipo() == TipoMovimentoEstoque.SAIDA_CONSUMO && req.centroCustoId() == null) {
            throw new BusinessException(Constants.ESTOQUE_CENTRO_CUSTO_OBRIGATORIO, HttpStatus.BAD_REQUEST);
        }
    }

    private EstoqueSaldo buscarOuCriarSaldo(MovimentoRequisicao req, UUID produtoId) {
        return estoqueSaldoRepository
                .findByProdutoIdAndDepositoIdForUpdate(req.tenantId(), produtoId, req.depositoId())
                .orElseGet(() -> {
                    EstoqueSaldo novo = EstoqueSaldo.builder()
                            .produtoId(produtoId)
                            .depositoId(req.depositoId())
                            .quantidade(BigDecimal.ZERO)
                            .createdAt(req.ocorridoEm())
                            .createdBy(req.userId())
                            .build();
                    novo.setTenantId(req.tenantId());
                    return novo;
                });
    }

    /** RN-EST-07: duplo-clique/retry no mesmo documento vira 409, não 500 (índice único parcial). */
    private MovimentoEstoque gravarMovimento(MovimentoRequisicao req, UUID produtoId, BigDecimal quantidadeTotal,
                                              BigDecimal valorUnitario) {
        MovimentoEstoque movimento = MovimentoEstoque.builder()
                .produtoId(produtoId)
                .depositoId(req.depositoId())
                .tipo(req.tipo())
                .quantidade(quantidadeTotal)
                .valorUnitario(valorUnitario)
                .origemTipo(req.origemTipo())
                .origemId(req.origemId())
                .motivo(req.motivo())
                .tipoAjuste(req.tipoAjuste())
                .documentoReferencia(req.documentoReferencia())
                .centroCustoId(req.centroCustoId())
                .usuarioId(req.userId())
                .ocorridoEm(req.ocorridoEm())
                .createdAt(req.ocorridoEm())
                .createdBy(req.userId())
                .build();
        movimento.setTenantId(req.tenantId());
        try {
            movimentoEstoqueRepository.saveAndFlush(movimento);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(Constants.ESTOQUE_MOVIMENTO_DUPLICADO, HttpStatus.CONFLICT);
        }
        return movimento;
    }

    private BigDecimal mediaPonderada(List<MovimentoRequisicao.Linha> linhas, BigDecimal quantidadeTotal) {
        if (linhas.stream().allMatch(l -> l.valorUnitario() == null)) {
            return null;
        }
        BigDecimal soma = linhas.stream()
                .map(l -> l.quantidade().multiply(l.valorUnitario() != null ? l.valorUnitario() : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return soma.divide(quantidadeTotal, 4, RoundingMode.HALF_UP);
    }

    /**
     * Payload de {@link #registrarMovimento} (spec/modulos/estoque/estoque.md §2.2/§12). {@code tipoAjuste}
     * (RN-EST-11) e {@code centroCustoId} (RN-EST-10) só se aplicam ajuste/inventário e consumo, respectivamente;
     * {@code null} nos demais tipos de movimento. {@code permitirSaldoNegativo} (RN-EST-12) só tem efeito pra
     * REVENDA/USO_CONSUMO/MATERIA_PRIMA — PRODUTO_ACABADO já é sempre permitido.
     */
    public record MovimentoRequisicao(
            Long tenantId,
            UUID userId,
            TipoMovimentoEstoque tipo,
            OrigemMovimentoEstoque origemTipo,
            UUID origemId,
            UUID depositoId,
            Instant ocorridoEm,
            String motivo,
            UUID centroCustoId,
            TipoAjusteEstoque tipoAjuste,
            String documentoReferencia,
            List<Linha> linhas,
            boolean permitirSaldoNegativo) {

        // ponytail: overload preserva as chamadas existentes (PedidoService/RecebimentoMercadoriaService)
        // que nunca precisam de override de bloqueio.
        public MovimentoRequisicao(Long tenantId, UUID userId, TipoMovimentoEstoque tipo,
                                    OrigemMovimentoEstoque origemTipo, UUID origemId, UUID depositoId,
                                    Instant ocorridoEm, String motivo, UUID centroCustoId,
                                    TipoAjusteEstoque tipoAjuste, String documentoReferencia, List<Linha> linhas) {
            this(tenantId, userId, tipo, origemTipo, origemId, depositoId, ocorridoEm, motivo, centroCustoId,
                    tipoAjuste, documentoReferencia, linhas, false);
        }

        public record Linha(UUID produtoId, BigDecimal quantidade, BigDecimal valorUnitario) {
        }
    }

    /** Payload de {@link #ajustar} — espelha o corpo de {@code POST /api/v1/estoque/ajustes} (§5.3). */
    public record AjusteRequisicao(
            Long tenantId,
            UUID userId,
            UUID produtoId,
            UUID depositoId,
            BigDecimal quantidadeContada,
            OrigemMovimentoEstoque origem,
            TipoAjusteEstoque tipoAjuste,
            String motivo,
            String documentoReferencia,
            BigDecimal valorUnitario,
            boolean permitirSaldoNegativo) {

        // ponytail: overload preserva as chamadas existentes sem override de bloqueio.
        public AjusteRequisicao(Long tenantId, UUID userId, UUID produtoId, UUID depositoId,
                                 BigDecimal quantidadeContada, OrigemMovimentoEstoque origem,
                                 TipoAjusteEstoque tipoAjuste, String motivo, String documentoReferencia,
                                 BigDecimal valorUnitario) {
            this(tenantId, userId, produtoId, depositoId, quantidadeContada, origem, tipoAjuste, motivo,
                    documentoReferencia, valorUnitario, false);
        }
    }
}
