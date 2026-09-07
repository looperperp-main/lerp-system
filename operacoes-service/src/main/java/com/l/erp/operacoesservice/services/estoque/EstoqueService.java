package com.l.erp.operacoesservice.services.estoque;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.domain.estoque.EstoqueSaldo;
import com.l.erp.operacoesservice.domain.estoque.MovimentoEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.OrigemMovimentoEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.TipoMovimentoEstoque;
import com.l.erp.operacoesservice.repository.estoque.EstoqueSaldoRepository;
import com.l.erp.operacoesservice.repository.estoque.MovimentoEstoqueRepository;
import org.springframework.beans.factory.annotation.Value;
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
 * Caminho único de escrita do estoque (spec/estoque.md §4) — expedição, cancelamento, recebimento
 * e ajuste passam todos por {@link #registrarMovimento}, na mesma transação do chamador
 * ({@code @Transactional} sem {@code REQUIRES_NEW}).
 *
 * {@code ponytail:} uma classe concreta, um método, sem interface — há uma implementação e ela
 * não muda dentro do mesmo processo (§2.2).
 */
@Service
public class EstoqueService {

    // RN-EST-05: só saída "de verdade" bloqueia com a flag ligada; estorno e ajuste pra cima nunca.
    private static final Set<TipoMovimentoEstoque> TIPOS_ENTRADA = EnumSet.of(
            TipoMovimentoEstoque.ENTRADA_COMPRA, TipoMovimentoEstoque.ESTORNO_SAIDA_VENDA,
            TipoMovimentoEstoque.AJUSTE_ENTRADA);
    private static final Set<TipoMovimentoEstoque> TIPOS_SUJEITOS_A_BLOQUEIO = EnumSet.of(
            TipoMovimentoEstoque.SAIDA_VENDA, TipoMovimentoEstoque.AJUSTE_SAIDA);
    private static final Set<OrigemMovimentoEstoque> ORIGENS_SEM_DOCUMENTO = EnumSet.of(
            OrigemMovimentoEstoque.AJUSTE, OrigemMovimentoEstoque.INVENTARIO);

    private final MovimentoEstoqueRepository movimentoEstoqueRepository;
    private final EstoqueSaldoRepository estoqueSaldoRepository;

    @Value("${estoque.bloquear-saida:false}")
    private boolean bloquearSaida;

    public EstoqueService(MovimentoEstoqueRepository movimentoEstoqueRepository,
                           EstoqueSaldoRepository estoqueSaldoRepository) {
        this.movimentoEstoqueRepository = movimentoEstoqueRepository;
        this.estoqueSaldoRepository = estoqueSaldoRepository;
    }

    /** A API in-process inteira do lado da escrita (spec/estoque.md §2.2/§4.1). */
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
            BigDecimal valorUnitario = mediaPonderada(linhasDoProduto, quantidadeTotal);

            EstoqueSaldo saldo = buscarOuCriarSaldo(req, produtoId);
            BigDecimal saldoAnterior = saldo.getQuantidade();
            BigDecimal saldoNovo = entrada ? saldoAnterior.add(quantidadeTotal) : saldoAnterior.subtract(quantidadeTotal);

            if (bloquearSaida && TIPOS_SUJEITOS_A_BLOQUEIO.contains(req.tipo()) && saldoNovo.signum() < 0) {
                throw new BusinessException(String.format(Constants.ESTOQUE_SALDO_INSUFICIENTE,
                        produtoId, req.depositoId(), saldoAnterior, req.origemId()), HttpStatus.BAD_REQUEST);
            }

            saldo.setQuantidade(saldoNovo);
            saldo.setUpdatedAt(req.ocorridoEm());
            saldo.setLastUpdatedBy(req.userId());
            estoqueSaldoRepository.save(saldo);

            gravarMovimento(req, produtoId, quantidadeTotal, valorUnitario);
        }
    }

    /**
     * Ajuste/inventário por saldo contado (spec/estoque.md §5.3, D5): o operador informa o saldo
     * que de fato existe, não a diferença — elimina erro de sinal invertido.
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
                req.depositoId(), agora, req.motivo(),
                List.of(new MovimentoRequisicao.Linha(req.produtoId(), delta.abs(), req.valorUnitario()))));
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
            if (req.motivo() == null || req.motivo().isBlank()) {
                throw new BusinessException(Constants.ESTOQUE_MOTIVO_OBRIGATORIO, HttpStatus.BAD_REQUEST);
            }
            if (req.motivo().length() > 500) {
                throw new BusinessException(Constants.ESTOQUE_MOTIVO_TAMANHO_INVALIDO, HttpStatus.BAD_REQUEST);
            }
        } else if (req.origemId() == null) {
            throw new BusinessException(Constants.ESTOQUE_ORIGEM_ID_OBRIGATORIO, HttpStatus.BAD_REQUEST);
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
    private void gravarMovimento(MovimentoRequisicao req, UUID produtoId, BigDecimal quantidadeTotal,
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
                .usuarioId(req.userId())
                .ocorridoEm(req.ocorridoEm())
                .createdAt(req.ocorridoEm())
                .createdBy(req.userId())
                .build();
        movimento.setTenantId(req.tenantId());
        try {
            // saveAndFlush força o INSERT agora, dentro do try — senão o JPA adia o flush pro fim
            // da transação e a violação do índice único estouraria depois deste método já ter
            // retornado, sem chance de virar 409 aqui (mesmo padrão de CheckoutService).
            movimentoEstoqueRepository.saveAndFlush(movimento);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(Constants.ESTOQUE_MOVIMENTO_DUPLICADO, HttpStatus.CONFLICT);
        }
    }

    /**
     * Soma ponderada pela quantidade de cada linha. {@code ponytail:} caso raro (documento com
     * mais de uma linha do mesmo produto); com todas as linhas sem preço, resultado é null.
     */
    private BigDecimal mediaPonderada(List<MovimentoRequisicao.Linha> linhas, BigDecimal quantidadeTotal) {
        if (linhas.stream().allMatch(l -> l.valorUnitario() == null)) {
            return null;
        }
        BigDecimal soma = linhas.stream()
                .map(l -> l.quantidade().multiply(l.valorUnitario() != null ? l.valorUnitario() : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return soma.divide(quantidadeTotal, 4, RoundingMode.HALF_UP);
    }

    /** spec/estoque.md §2.2 — record base não traz {@code motivo}; adicionado aqui porque §4.1
     * passo 1 e RN-EST-06 exigem validá-lo e a coluna {@code movimento_estoque.motivo} precisa
     * dele para ajuste/inventário. */
    public record MovimentoRequisicao(
            Long tenantId,
            UUID userId,
            TipoMovimentoEstoque tipo,
            OrigemMovimentoEstoque origemTipo,
            UUID origemId,
            UUID depositoId,
            Instant ocorridoEm,
            String motivo,
            List<Linha> linhas) {

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
            String motivo,
            BigDecimal valorUnitario) {
    }
}
