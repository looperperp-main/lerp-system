package com.l.erp.operacoesservice.services.estoque;

import com.l.erp.common.exception.custom.BusinessException;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstoqueServiceTest {

    @Mock
    private MovimentoEstoqueRepository movimentoEstoqueRepository;
    @Mock
    private EstoqueSaldoRepository estoqueSaldoRepository;
    @Mock
    private PendenciaEstoqueRepository pendenciaEstoqueRepository;
    @Mock
    private FechamentoEstoqueRepository fechamentoEstoqueRepository;
    @Mock
    private CadastroServiceClient cadastroServiceClient;

    @InjectMocks
    private EstoqueService estoqueService;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PRODUTO_ID = UUID.randomUUID();
    private static final UUID DEPOSITO_ID = UUID.randomUUID();

    private EstoqueSaldo saldoExistente(BigDecimal quantidade) {
        EstoqueSaldo saldo = EstoqueSaldo.builder()
                .produtoId(PRODUTO_ID)
                .depositoId(DEPOSITO_ID)
                .quantidade(quantidade)
                .createdAt(Instant.now())
                .createdBy(USER_ID)
                .build();
        saldo.setTenantId(TENANT_ID);
        return saldo;
    }

    private EstoqueService.MovimentoRequisicao requisicao(TipoMovimentoEstoque tipo, BigDecimal quantidade) {
        return new EstoqueService.MovimentoRequisicao(TENANT_ID, USER_ID, tipo, OrigemMovimentoEstoque.PEDIDO_VENDA,
                UUID.randomUUID(), DEPOSITO_ID, Instant.now(), null, null, null, null,
                List.of(new EstoqueService.MovimentoRequisicao.Linha(PRODUTO_ID, quantidade, BigDecimal.TEN)));
    }

    // ---------------------------------------------------------------- registrarMovimento

    @Test
    void entradaEmProdutoSemSaldo_criaSaldoComAQuantidadeEGrava1Movimento() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.empty());

        estoqueService.registrarMovimento(requisicao(TipoMovimentoEstoque.ENTRADA_COMPRA, BigDecimal.TEN));

        ArgumentCaptor<EstoqueSaldo> captor = ArgumentCaptor.forClass(EstoqueSaldo.class);
        verify(estoqueSaldoRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantidade()).isEqualByComparingTo(BigDecimal.TEN);
        verify(movimentoEstoqueRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void entradaEmProdutoComSaldo_somaENaoDuplicaLinhaDeSaldo() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(saldoExistente(new BigDecimal("5"))));

        estoqueService.registrarMovimento(requisicao(TipoMovimentoEstoque.ENTRADA_COMPRA, BigDecimal.TEN));

        ArgumentCaptor<EstoqueSaldo> captor = ArgumentCaptor.forClass(EstoqueSaldo.class);
        verify(estoqueSaldoRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getQuantidade()).isEqualByComparingTo(new BigDecimal("15"));
    }

    @Test
    void saida_subtrai() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(saldoExistente(new BigDecimal("20"))));

        estoqueService.registrarMovimento(requisicao(TipoMovimentoEstoque.SAIDA_VENDA, BigDecimal.TEN));

        ArgumentCaptor<EstoqueSaldo> captor = ArgumentCaptor.forClass(EstoqueSaldo.class);
        verify(estoqueSaldoRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantidade()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void saidaMaiorQueSaldoRevendaSemOverride_lancaBusinessException400ENadaGravado() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(saldoExistente(new BigDecimal("5"))));
        when(cadastroServiceClient.buscarProduto(PRODUTO_ID, TENANT_ID, USER_ID))
                .thenReturn(new CadastroServiceClient.ProdutoRef("MERCADORIA", null, true, null, null, "Produto", "REVENDA"));

        assertThatThrownBy(() -> estoqueService.registrarMovimento(
                requisicao(TipoMovimentoEstoque.SAIDA_VENDA, BigDecimal.TEN)))
                .isInstanceOf(BusinessException.class);

        verify(estoqueSaldoRepository, never()).save(any());
        verify(movimentoEstoqueRepository, never()).saveAndFlush(any());
        verifyNoInteractions(pendenciaEstoqueRepository);
    }

    @Test
    void saidaMaiorQueSaldoRevendaComOverride_permiteEDeixaSaldoNegativoECriaPendenciaRegularizacao() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(saldoExistente(new BigDecimal("5"))));
        when(cadastroServiceClient.buscarProduto(PRODUTO_ID, TENANT_ID, USER_ID))
                .thenReturn(new CadastroServiceClient.ProdutoRef("MERCADORIA", null, true, null, null, "Produto", "REVENDA"));
        when(movimentoEstoqueRepository.saveAndFlush(any())).thenAnswer(inv -> {
            MovimentoEstoque m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        EstoqueService.MovimentoRequisicao req = new EstoqueService.MovimentoRequisicao(TENANT_ID, USER_ID,
                TipoMovimentoEstoque.SAIDA_VENDA, OrigemMovimentoEstoque.PEDIDO_VENDA, UUID.randomUUID(), DEPOSITO_ID,
                Instant.now(), null, null, null, null,
                List.of(new EstoqueService.MovimentoRequisicao.Linha(PRODUTO_ID, BigDecimal.TEN, null)), true);
        estoqueService.registrarMovimento(req);

        ArgumentCaptor<EstoqueSaldo> saldoCaptor = ArgumentCaptor.forClass(EstoqueSaldo.class);
        verify(estoqueSaldoRepository).save(saldoCaptor.capture());
        assertThat(saldoCaptor.getValue().getQuantidade()).isEqualByComparingTo(new BigDecimal("-5"));

        ArgumentCaptor<PendenciaEstoque> pendenciaCaptor = ArgumentCaptor.forClass(PendenciaEstoque.class);
        verify(pendenciaEstoqueRepository).save(pendenciaCaptor.capture());
        assertThat(pendenciaCaptor.getValue().getTipo()).isEqualTo(PendenciaTipoEstoque.REGULARIZACAO);
    }

    @Test
    void saidaMaiorQueSaldoProdutoAcabado_permiteSempreECriaPendenciaApontarProducao() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(saldoExistente(new BigDecimal("5"))));
        when(cadastroServiceClient.buscarProduto(PRODUTO_ID, TENANT_ID, USER_ID))
                .thenReturn(new CadastroServiceClient.ProdutoRef("MERCADORIA", null, true, null, null, "Produto", "PRODUTO_ACABADO"));
        when(movimentoEstoqueRepository.saveAndFlush(any())).thenAnswer(inv -> {
            MovimentoEstoque m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        // permitirSaldoNegativo=false (default) — PRODUTO_ACABADO nunca bloqueia, não é opt-in (RN-EST-12).
        estoqueService.registrarMovimento(requisicao(TipoMovimentoEstoque.SAIDA_VENDA, BigDecimal.TEN));

        verify(estoqueSaldoRepository).save(any());
        ArgumentCaptor<PendenciaEstoque> pendenciaCaptor = ArgumentCaptor.forClass(PendenciaEstoque.class);
        verify(pendenciaEstoqueRepository).save(pendenciaCaptor.capture());
        assertThat(pendenciaCaptor.getValue().getTipo()).isEqualTo(PendenciaTipoEstoque.APONTAR_PRODUCAO);
    }

    @Test
    void estornoSaidaVendaComSaldoNegativo_passaPoisEstornoNuncaEBloqueado() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(saldoExistente(new BigDecimal("-5"))));

        estoqueService.registrarMovimento(requisicao(TipoMovimentoEstoque.ESTORNO_SAIDA_VENDA, BigDecimal.TEN));

        verify(movimentoEstoqueRepository, times(1)).saveAndFlush(any());
        verifyNoInteractions(cadastroServiceClient, pendenciaEstoqueRepository);
    }

    @Test
    void duasLinhasDoMesmoProduto_agregaEm1MovimentoComASoma() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.empty());
        EstoqueService.MovimentoRequisicao req = new EstoqueService.MovimentoRequisicao(TENANT_ID, USER_ID,
                TipoMovimentoEstoque.ENTRADA_COMPRA, OrigemMovimentoEstoque.RECEBIMENTO, UUID.randomUUID(),
                DEPOSITO_ID, Instant.now(), null, null, null, null, List.of(
                        new EstoqueService.MovimentoRequisicao.Linha(PRODUTO_ID, new BigDecimal("3"), BigDecimal.TEN),
                        new EstoqueService.MovimentoRequisicao.Linha(PRODUTO_ID, new BigDecimal("7"), BigDecimal.ONE)));

        estoqueService.registrarMovimento(req);

        verify(movimentoEstoqueRepository, times(1)).saveAndFlush(any());
        ArgumentCaptor<EstoqueSaldo> captor = ArgumentCaptor.forClass(EstoqueSaldo.class);
        verify(estoqueSaldoRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantidade()).isEqualByComparingTo(BigDecimal.TEN);
    }

    // ---------------------------------------------------------------- custo médio (RN-EST-09, D8)

    @Test
    void entradaComPrecoESaldoExistente_recalculaCustoMedioPonderado() {
        EstoqueSaldo existente = saldoExistente(new BigDecimal("10"));
        existente.setCustoMedio(new BigDecimal("5.0000"));
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(existente));
        EstoqueService.MovimentoRequisicao req = new EstoqueService.MovimentoRequisicao(TENANT_ID, USER_ID,
                TipoMovimentoEstoque.ENTRADA_COMPRA, OrigemMovimentoEstoque.RECEBIMENTO, UUID.randomUUID(),
                DEPOSITO_ID, Instant.now(), null, null, null, null,
                List.of(new EstoqueService.MovimentoRequisicao.Linha(PRODUTO_ID, new BigDecimal("10"), new BigDecimal("8"))));

        estoqueService.registrarMovimento(req);

        // (10*5 + 10*8) / 20 = 6.5
        ArgumentCaptor<EstoqueSaldo> captor = ArgumentCaptor.forClass(EstoqueSaldo.class);
        verify(estoqueSaldoRepository).save(captor.capture());
        assertThat(captor.getValue().getCustoMedio()).isEqualByComparingTo("6.5000");
    }

    @Test
    void saidaVenda_gravaValorUnitarioComoCustoMedioVigente_ignorandoValorInformadoNaLinha() {
        EstoqueSaldo existente = saldoExistente(new BigDecimal("20"));
        existente.setCustoMedio(new BigDecimal("7.5000"));
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(existente));

        // requisicao() manda valorUnitario=TEN na linha — deve ser ignorado na saída.
        estoqueService.registrarMovimento(requisicao(TipoMovimentoEstoque.SAIDA_VENDA, BigDecimal.TEN));

        ArgumentCaptor<MovimentoEstoque> captor = ArgumentCaptor.forClass(MovimentoEstoque.class);
        verify(movimentoEstoqueRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getValorUnitario()).isEqualByComparingTo("7.5000");
    }

    // ---------------------------------------------------------------- SAIDA_CONSUMO (RN-EST-10, D7)

    @Test
    void consumoSemCentroCusto_lanca400() {
        EstoqueService.MovimentoRequisicao req = new EstoqueService.MovimentoRequisicao(TENANT_ID, USER_ID,
                TipoMovimentoEstoque.SAIDA_CONSUMO, OrigemMovimentoEstoque.CONSUMO, null, DEPOSITO_ID,
                Instant.now(), null, null, null, null,
                List.of(new EstoqueService.MovimentoRequisicao.Linha(PRODUTO_ID, BigDecimal.TEN, null)));

        assertThatThrownBy(() -> estoqueService.registrarMovimento(req))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(movimentoEstoqueRepository);
    }

    @Test
    void consumoComCentroCustoEOrigemIdNulo_gravaMovimentoComCentroCustoECustoMedio() {
        UUID centroCustoId = UUID.randomUUID();
        EstoqueSaldo existente = saldoExistente(new BigDecimal("20"));
        existente.setCustoMedio(new BigDecimal("4.0000"));
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(existente));
        EstoqueService.MovimentoRequisicao req = new EstoqueService.MovimentoRequisicao(TENANT_ID, USER_ID,
                TipoMovimentoEstoque.SAIDA_CONSUMO, OrigemMovimentoEstoque.CONSUMO, null, DEPOSITO_ID,
                Instant.now(), null, centroCustoId, null, null,
                List.of(new EstoqueService.MovimentoRequisicao.Linha(PRODUTO_ID, BigDecimal.TEN, null)));

        estoqueService.registrarMovimento(req);

        ArgumentCaptor<MovimentoEstoque> captor = ArgumentCaptor.forClass(MovimentoEstoque.class);
        verify(movimentoEstoqueRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCentroCustoId()).isEqualTo(centroCustoId);
        assertThat(captor.getValue().getValorUnitario()).isEqualByComparingTo("4.0000");
    }

    // ---------------------------------------------------------------- ajustar

    @Test
    void ajusteComQuantidadeContadaMaiorQueSaldo_gravaAjusteEntradaComODelta() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(saldoExistente(new BigDecimal("5"))));

        estoqueService.ajustar(new EstoqueService.AjusteRequisicao(TENANT_ID, USER_ID, PRODUTO_ID, DEPOSITO_ID,
                new BigDecimal("8"), OrigemMovimentoEstoque.INVENTARIO, TipoAjusteEstoque.INVENTARIO,
                "contagem cíclica", null, BigDecimal.TEN));

        verify(movimentoEstoqueRepository, times(1)).saveAndFlush(any());
        ArgumentCaptor<EstoqueSaldo> captor = ArgumentCaptor.forClass(EstoqueSaldo.class);
        verify(estoqueSaldoRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantidade()).isEqualByComparingTo(new BigDecimal("8"));
    }

    @Test
    void ajusteComQuantidadeContadaMenorQueSaldo_gravaAjusteSaidaComOModuloDoDelta() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(saldoExistente(new BigDecimal("10"))));

        estoqueService.ajustar(new EstoqueService.AjusteRequisicao(TENANT_ID, USER_ID, PRODUTO_ID, DEPOSITO_ID,
                new BigDecimal("3"), OrigemMovimentoEstoque.AJUSTE, TipoAjusteEstoque.AVARIA, "avaria", null, null));

        ArgumentCaptor<EstoqueSaldo> captor = ArgumentCaptor.forClass(EstoqueSaldo.class);
        verify(estoqueSaldoRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantidade()).isEqualByComparingTo(new BigDecimal("3"));
    }

    @Test
    void ajusteComQuantidadeContadaIgualAoSaldo_naoGravaNadaNoOp() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(saldoExistente(new BigDecimal("10"))));

        estoqueService.ajustar(new EstoqueService.AjusteRequisicao(TENANT_ID, USER_ID, PRODUTO_ID, DEPOSITO_ID,
                BigDecimal.TEN, OrigemMovimentoEstoque.AJUSTE, TipoAjusteEstoque.INVENTARIO, "confirmação", null, null));

        verifyNoInteractions(movimentoEstoqueRepository);
        verify(estoqueSaldoRepository, never()).save(any());
    }

    @Test
    void ajusteSemTipoAjuste_lanca400() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(saldoExistente(new BigDecimal("5"))));

        assertThatThrownBy(() -> estoqueService.ajustar(new EstoqueService.AjusteRequisicao(TENANT_ID, USER_ID,
                PRODUTO_ID, DEPOSITO_ID, BigDecimal.TEN, OrigemMovimentoEstoque.AJUSTE, null, "avaria", null, null)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(movimentoEstoqueRepository);
    }

    // ---------------------------------------------------------------- ajuste tipificado (RN-EST-11, D9)

    @Test
    void ajusteEntradaSemCustoForaDeSaldoInicial_lanca400() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.of(saldoExistente(new BigDecimal("5"))));

        assertThatThrownBy(() -> estoqueService.ajustar(new EstoqueService.AjusteRequisicao(TENANT_ID, USER_ID,
                PRODUTO_ID, DEPOSITO_ID, new BigDecimal("8"), OrigemMovimentoEstoque.INVENTARIO,
                TipoAjusteEstoque.BONIFICACAO_RECEBIDA, "bonificação", null, null)))
                .isInstanceOf(BusinessException.class);
        verify(movimentoEstoqueRepository, never()).saveAndFlush(any());
    }

    @Test
    void ajusteEntradaSaldoInicialSemCusto_permiteMesmoAssim() {
        when(estoqueSaldoRepository.findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, PRODUTO_ID, DEPOSITO_ID))
                .thenReturn(Optional.empty());

        estoqueService.ajustar(new EstoqueService.AjusteRequisicao(TENANT_ID, USER_ID, PRODUTO_ID, DEPOSITO_ID,
                new BigDecimal("100"), OrigemMovimentoEstoque.AJUSTE, TipoAjusteEstoque.SALDO_INICIAL,
                "carga inicial", null, null));

        verify(movimentoEstoqueRepository, times(1)).saveAndFlush(any());
    }

    // ---------------------------------------------------------------- validação

    @Test
    void quantidadeMenorOuIgualAZeroNaRequisicao_lanca400() {
        assertThatThrownBy(() -> estoqueService.registrarMovimento(
                requisicao(TipoMovimentoEstoque.SAIDA_VENDA, BigDecimal.ZERO)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(movimentoEstoqueRepository);
        verifyNoInteractions(estoqueSaldoRepository);
    }

    @Test
    void movimentoComOrigemDocumentalEOrigemIdNull_lanca400() {
        EstoqueService.MovimentoRequisicao req = new EstoqueService.MovimentoRequisicao(TENANT_ID, USER_ID,
                TipoMovimentoEstoque.SAIDA_VENDA, OrigemMovimentoEstoque.PEDIDO_VENDA, null, DEPOSITO_ID,
                Instant.now(), null, null, null, null,
                List.of(new EstoqueService.MovimentoRequisicao.Linha(PRODUTO_ID, BigDecimal.TEN, null)));

        assertThatThrownBy(() -> estoqueService.registrarMovimento(req))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(movimentoEstoqueRepository);
        verifyNoInteractions(estoqueSaldoRepository);
    }

    // ---------------------------------------------------------------- pendência / fechamento (RN-EST-12/13)

    @Test
    void resolverPendenciaJaResolvida_lanca409() {
        PendenciaEstoque pendencia = PendenciaEstoque.builder().id(UUID.randomUUID()).resolvida(true).build();
        when(pendenciaEstoqueRepository.findByIdAndTenantId(pendencia.getId(), TENANT_ID))
                .thenReturn(Optional.of(pendencia));

        assertThatThrownBy(() -> estoqueService.resolverPendencia(TENANT_ID, pendencia.getId(), USER_ID))
                .isInstanceOf(BusinessException.class);
        verify(pendenciaEstoqueRepository, never()).save(any());
    }

    @Test
    void resolverPendenciaAberta_marcaResolvida() {
        PendenciaEstoque pendencia = PendenciaEstoque.builder().id(UUID.randomUUID()).resolvida(false).build();
        when(pendenciaEstoqueRepository.findByIdAndTenantId(pendencia.getId(), TENANT_ID))
                .thenReturn(Optional.of(pendencia));

        estoqueService.resolverPendencia(TENANT_ID, pendencia.getId(), USER_ID);

        ArgumentCaptor<PendenciaEstoque> captor = ArgumentCaptor.forClass(PendenciaEstoque.class);
        verify(pendenciaEstoqueRepository).save(captor.capture());
        assertThat(captor.getValue().getResolvida()).isTrue();
        assertThat(captor.getValue().getResolvidoPor()).isEqualTo(USER_ID);
    }

    @Test
    void fecharPeriodoComPendenciaAberta_lanca409ENadaGravado() {
        when(fechamentoEstoqueRepository.existsByTenantIdAndCompetencia(TENANT_ID, "2026-09")).thenReturn(false);
        when(pendenciaEstoqueRepository.existsByTenantIdAndResolvidaFalse(TENANT_ID)).thenReturn(true);

        assertThatThrownBy(() -> estoqueService.fecharPeriodo(TENANT_ID, "2026-09", USER_ID))
                .isInstanceOf(BusinessException.class);
        verify(fechamentoEstoqueRepository, never()).save(any());
    }

    @Test
    void fecharPeriodoComSaldoNegativo_lanca409ENadaGravado() {
        when(fechamentoEstoqueRepository.existsByTenantIdAndCompetencia(TENANT_ID, "2026-09")).thenReturn(false);
        when(pendenciaEstoqueRepository.existsByTenantIdAndResolvidaFalse(TENANT_ID)).thenReturn(false);
        when(estoqueSaldoRepository.existsByTenantIdAndQuantidadeLessThan(TENANT_ID, BigDecimal.ZERO)).thenReturn(true);

        assertThatThrownBy(() -> estoqueService.fecharPeriodo(TENANT_ID, "2026-09", USER_ID))
                .isInstanceOf(BusinessException.class);
        verify(fechamentoEstoqueRepository, never()).save(any());
    }

    @Test
    void fecharPeriodoSemPendenciaNemSaldoNegativo_grava() {
        when(fechamentoEstoqueRepository.existsByTenantIdAndCompetencia(TENANT_ID, "2026-09")).thenReturn(false);
        when(pendenciaEstoqueRepository.existsByTenantIdAndResolvidaFalse(TENANT_ID)).thenReturn(false);
        when(estoqueSaldoRepository.existsByTenantIdAndQuantidadeLessThan(TENANT_ID, BigDecimal.ZERO)).thenReturn(false);

        estoqueService.fecharPeriodo(TENANT_ID, "2026-09", USER_ID);

        verify(fechamentoEstoqueRepository).save(any(FechamentoEstoque.class));
    }

    @Test
    void fecharPeriodoComCompetenciaInvalida_lanca400() {
        assertThatThrownBy(() -> estoqueService.fecharPeriodo(TENANT_ID, "09/2026", USER_ID))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(fechamentoEstoqueRepository);
    }

    @Test
    void registrarMovimentoComCompetenciaJaFechada_lanca409ENaoGravaNada() {
        Instant ocorridoEm = Instant.parse("2026-09-15T12:00:00Z");
        EstoqueService.MovimentoRequisicao req = new EstoqueService.MovimentoRequisicao(TENANT_ID, USER_ID,
                TipoMovimentoEstoque.ENTRADA_COMPRA, OrigemMovimentoEstoque.RECEBIMENTO, UUID.randomUUID(),
                DEPOSITO_ID, ocorridoEm, null, null, null, null,
                List.of(new EstoqueService.MovimentoRequisicao.Linha(PRODUTO_ID, BigDecimal.TEN, BigDecimal.TEN)));
        when(fechamentoEstoqueRepository.existsByTenantIdAndCompetencia(TENANT_ID, "2026-09")).thenReturn(true);

        assertThatThrownBy(() -> estoqueService.registrarMovimento(req))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(estoqueSaldoRepository, movimentoEstoqueRepository);
    }
}
