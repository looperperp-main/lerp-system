package com.l.erp.operacoesservice.services.vendas;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.operacoesservice.domain.vendas.Pedido;
import com.l.erp.operacoesservice.domain.vendas.PedidoItem;
import com.l.erp.operacoesservice.domain.vendas.enumerators.ModalidadeFrete;
import com.l.erp.operacoesservice.domain.vendas.enumerators.StatusPedido;
import com.l.erp.operacoesservice.repository.vendas.PedidoItemRepository;
import com.l.erp.operacoesservice.repository.vendas.PedidoRepository;
import com.l.erp.operacoesservice.repository.vendas.PedidoStatusHistoricoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private PedidoItemRepository pedidoItemRepository;
    @Mock
    private PedidoStatusHistoricoRepository pedidoStatusHistoricoRepository;
    @Mock
    private PedidoNumeroService pedidoNumeroService;

    @InjectMocks
    private PedidoService pedidoService;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CLIENTE_ID = UUID.randomUUID();

    private PedidoItem item(BigDecimal quantidade, BigDecimal precoUnitario) {
        return PedidoItem.builder()
                .produtoId(UUID.randomUUID())
                .quantidade(quantidade)
                .precoUnitario(precoUnitario)
                .build();
    }

    /**
     * ponytail: tenantId vem de BaseTenantEntity (campo herdado) — Lombok {@code @Builder} não
     * inclui campos de superclasse no builder gerado, só {@code @SuperBuilder} faria isso (não usado
     * neste domínio). Setter pós-build é o mesmo padrão que o próprio PedidoService usa em produção.
     */
    private Pedido pedidoComTenant(Pedido pedido) {
        pedido.setTenantId(TENANT_ID);
        return pedido;
    }

    // ---------------------------------------------------------------- criarOrcamento

    @Test
    void deveLancarSeNaoHaItens() {
        assertThatThrownBy(() -> pedidoService.criarOrcamento(Pedido.builder().build(), List.of(), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarSeItemSemPrecoUnitario_stubDoResolver() {
        PedidoItem semPreco = PedidoItem.builder().produtoId(UUID.randomUUID())
                .quantidade(BigDecimal.ONE).build();

        assertThatThrownBy(() -> pedidoService.criarOrcamento(
                Pedido.builder().clienteId(CLIENTE_ID).build(), List.of(semPreco), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("preço vigente");
    }

    @Test
    void deveLancarSeProdutoDuplicadoNosItens() {
        UUID produtoId = UUID.randomUUID();
        PedidoItem item1 = PedidoItem.builder().produtoId(produtoId).quantidade(BigDecimal.ONE)
                .precoUnitario(BigDecimal.TEN).build();
        PedidoItem item2 = PedidoItem.builder().produtoId(produtoId).quantidade(BigDecimal.ONE)
                .precoUnitario(BigDecimal.TEN).build();

        assertThatThrownBy(() -> pedidoService.criarOrcamento(
                Pedido.builder().clienteId(CLIENTE_ID).build(), List.of(item1, item2), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveAceitarPrecoManualECalcularTotais() {
        PedidoItem item = item(new BigDecimal("2"), new BigDecimal("10.00"));
        Pedido pedido = Pedido.builder().clienteId(CLIENTE_ID).build();
        when(pedidoNumeroService.proximoNumero(TENANT_ID)).thenReturn(1L);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        Pedido salvo = pedidoService.criarOrcamento(pedido, List.of(item), TENANT_ID, USER_ID);

        assertThat(item.getPrecoManual()).isTrue();
        assertThat(item.getValorTotal()).isEqualByComparingTo("20.00");
        assertThat(salvo.getStatus()).isEqualTo(StatusPedido.ORCAMENTO);
        assertThat(salvo.getValorTotal()).isEqualByComparingTo("20.00");
        assertThat(salvo.getNumero()).isEqualTo(1L);
    }

    // ---------------------------------------------------------------- confirmar (§7, limite de crédito)

    private Pedido pedidoParaConfirmar(BigDecimal valorTotal) {
        return pedidoComTenant(Pedido.builder()
                .id(UUID.randomUUID())
                .clienteId(CLIENTE_ID)
                .status(StatusPedido.ORCAMENTO)
                .condicaoPagamentoId(UUID.randomUUID())
                .valorTotal(valorTotal)
                .build());
    }

    @Test
    void deveLancarNaConfirmacaoSeOrigemInvalida() {
        Pedido pedido = pedidoParaConfirmar(BigDecimal.TEN);
        pedido.setStatus(StatusPedido.FATURADO);
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoService.confirmar(pedido.getId(), TENANT_ID, USER_ID, false, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveConfirmarSemLimiteDefinido() {
        Pedido pedido = pedidoParaConfirmar(new BigDecimal("100.00"));
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.somaValorTotalPorStatus(eq(TENANT_ID), eq(CLIENTE_ID), anyCollection(), eq(pedido.getId())))
                .thenReturn(BigDecimal.ZERO);

        Pedido resultado = pedidoService.confirmar(pedido.getId(), TENANT_ID, USER_ID, false, null);

        assertThat(resultado.getStatus()).isEqualTo(StatusPedido.CONFIRMADO);
    }

    @Test
    void deveConfirmarDentroDoLimite() {
        Pedido pedido = pedidoParaConfirmar(new BigDecimal("100.00"));
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.somaValorTotalPorStatus(eq(TENANT_ID), eq(CLIENTE_ID), anyCollection(), eq(pedido.getId())))
                .thenReturn(BigDecimal.ZERO);

        Pedido resultado = pedidoService.confirmar(pedido.getId(), TENANT_ID, USER_ID, false, new BigDecimal("500.00"));

        assertThat(resultado.getStatus()).isEqualTo(StatusPedido.CONFIRMADO);
    }

    @Test
    void deveBloquearPorCreditoQuandoEstouraLimiteSemPermissaoDeBypass() {
        Pedido pedido = pedidoParaConfirmar(new BigDecimal("600.00"));
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.somaValorTotalPorStatus(eq(TENANT_ID), eq(CLIENTE_ID), anyCollection(), eq(pedido.getId())))
                .thenReturn(BigDecimal.ZERO);

        Pedido resultado = pedidoService.confirmar(pedido.getId(), TENANT_ID, USER_ID, false, new BigDecimal("500.00"));

        assertThat(resultado.getStatus()).isEqualTo(StatusPedido.BLOQUEADO_CREDITO);
    }

    @Test
    void deveConfirmarComBypassMesmoEstourandoLimite() {
        Pedido pedido = pedidoParaConfirmar(new BigDecimal("600.00"));
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.somaValorTotalPorStatus(eq(TENANT_ID), eq(CLIENTE_ID), anyCollection(), eq(pedido.getId())))
                .thenReturn(BigDecimal.ZERO);

        Pedido resultado = pedidoService.confirmar(pedido.getId(), TENANT_ID, USER_ID, true, new BigDecimal("500.00"));

        assertThat(resultado.getStatus()).isEqualTo(StatusPedido.CONFIRMADO);
    }

    @Test
    void exposicaoDeveSomarPedidosLocaisJaConfirmadosOuExpedidos() {
        // pedido novo de 100 + 450 já expostos em outros pedidos do cliente = 550 > limite 500 → bloqueia
        Pedido pedido = pedidoParaConfirmar(new BigDecimal("100.00"));
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.somaValorTotalPorStatus(eq(TENANT_ID), eq(CLIENTE_ID), anyCollection(), eq(pedido.getId())))
                .thenReturn(new BigDecimal("450.00"));

        Pedido resultado = pedidoService.confirmar(pedido.getId(), TENANT_ID, USER_ID, false, new BigDecimal("500.00"));

        assertThat(resultado.getStatus()).isEqualTo(StatusPedido.BLOQUEADO_CREDITO);
    }

    // ---------------------------------------------------------------- reabrir / expedir / cancelar

    @Test
    void deveReabrirDeBloqueadoCreditoParaOrcamento() {
        Pedido pedido = pedidoComTenant(Pedido.builder().id(UUID.randomUUID())
                .status(StatusPedido.BLOQUEADO_CREDITO).build());
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        Pedido resultado = pedidoService.reabrir(pedido.getId(), TENANT_ID, USER_ID);

        assertThat(resultado.getStatus()).isEqualTo(StatusPedido.ORCAMENTO);
    }

    @Test
    void deveExpedirComDepositoInformado() {
        Pedido pedido = pedidoComTenant(Pedido.builder().id(UUID.randomUUID())
                .status(StatusPedido.CONFIRMADO).valorItens(new BigDecimal("100.00"))
                .valorDesconto(BigDecimal.ZERO).build());
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        Pedido resultado = pedidoService.expedir(pedido.getId(), TENANT_ID, USER_ID, UUID.randomUUID(), null,
                null, ModalidadeFrete.SEM_FRETE);

        assertThat(resultado.getStatus()).isEqualTo(StatusPedido.EXPEDIDO);
        assertThat(resultado.getValorTotal()).isEqualByComparingTo("100.00");
    }

    @Test
    void deveLancarNaExpedicaoSemDeposito() {
        Pedido pedido = pedidoComTenant(Pedido.builder().id(UUID.randomUUID())
                .status(StatusPedido.CONFIRMADO).build());
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoService.expedir(pedido.getId(), TENANT_ID, USER_ID, null, null,
                null, ModalidadeFrete.SEM_FRETE))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarNaExpedicaoComFreteSemTransportadora() {
        Pedido pedido = pedidoComTenant(Pedido.builder().id(UUID.randomUUID())
                .status(StatusPedido.CONFIRMADO).build());
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoService.expedir(pedido.getId(), TENANT_ID, USER_ID, UUID.randomUUID(), null,
                BigDecimal.TEN, ModalidadeFrete.CIF))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarNoCancelamentoSemMotivo() {
        assertThatThrownBy(() -> pedidoService.cancelar(UUID.randomUUID(), TENANT_ID, USER_ID, " "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoCancelarPedidoJaFaturado() {
        Pedido pedido = pedidoComTenant(Pedido.builder().id(UUID.randomUUID())
                .status(StatusPedido.FATURADO).build());
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoService.cancelar(pedido.getId(), TENANT_ID, USER_ID, "desistência"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveCancelarOrcamento() {
        Pedido pedido = pedidoComTenant(Pedido.builder().id(UUID.randomUUID())
                .status(StatusPedido.ORCAMENTO).build());
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        Pedido resultado = pedidoService.cancelar(pedido.getId(), TENANT_ID, USER_ID, "desistência");

        assertThat(resultado.getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(resultado.getMotivoCancelamento()).isEqualTo("desistência");
    }

    // ---------------------------------------------------------------- faturar (§8, parcelas)

    @Test
    void deveLancarSePercentualDasParcelasNaoSoma100() {
        Pedido pedido = pedidoComTenant(Pedido.builder().id(UUID.randomUUID())
                .status(StatusPedido.EXPEDIDO).valorTotal(new BigDecimal("100.00")).build());
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));
        List<PedidoService.ParcelaDefinicao> parcelas = List.of(
                new PedidoService.ParcelaDefinicao(1, 0, new BigDecimal("50"), "BOLETO"));

        assertThatThrownBy(() -> pedidoService.faturar(pedido.getId(), TENANT_ID, USER_ID, parcelas))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveFaturarERetornarParcelasComSomaExata() {
        Pedido pedido = pedidoComTenant(Pedido.builder().id(UUID.randomUUID())
                .status(StatusPedido.EXPEDIDO).valorTotal(new BigDecimal("100.00")).build());
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));
        List<PedidoService.ParcelaDefinicao> definicoes = List.of(
                new PedidoService.ParcelaDefinicao(1, 30, new BigDecimal("33.33"), "BOLETO"),
                new PedidoService.ParcelaDefinicao(2, 60, new BigDecimal("33.33"), "BOLETO"),
                new PedidoService.ParcelaDefinicao(3, 90, new BigDecimal("33.34"), "BOLETO"));

        PedidoService.FaturamentoResultado resultado =
                pedidoService.faturar(pedido.getId(), TENANT_ID, USER_ID, definicoes);

        assertThat(resultado.pedido().getStatus()).isEqualTo(StatusPedido.FATURADO);
        BigDecimal soma = resultado.parcelas().stream().map(PedidoService.ParcelaFaturamento::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(soma).isEqualByComparingTo("100.00");
        assertThat(resultado.parcelas().get(2).dataVencimento())
                .isEqualTo(LocalDate.now().plusDays(90));
    }

    @Test
    void calcularParcelas_arredondamentoSempreSomaExatoMesmoComDizimaPeriodica() {
        List<PedidoService.ParcelaDefinicao> definicoes = List.of(
                new PedidoService.ParcelaDefinicao(1, 0, new BigDecimal("33.33"), "PIX"),
                new PedidoService.ParcelaDefinicao(2, 0, new BigDecimal("33.33"), "PIX"),
                new PedidoService.ParcelaDefinicao(3, 0, new BigDecimal("33.34"), "PIX"));

        List<PedidoService.ParcelaFaturamento> parcelas =
                PedidoService.calcularParcelas(new BigDecimal("10.00"), LocalDate.now(), definicoes);

        BigDecimal soma = parcelas.stream().map(PedidoService.ParcelaFaturamento::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(soma).isEqualByComparingTo("10.00");
    }

    // ---------------------------------------------------------------- atualizar / recalcularPrecos (Fase 4, §5/§10)

    @Test
    void deveLancarAoAtualizarPedidoQueNaoEstaEmOrcamento() {
        Pedido pedido = pedidoComTenant(Pedido.builder().id(UUID.randomUUID())
                .status(StatusPedido.CONFIRMADO).clienteId(CLIENTE_ID).build());
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        Pedido dados = Pedido.builder().clienteId(CLIENTE_ID).build();
        List<PedidoItem> itens = List.of(item(BigDecimal.ONE, BigDecimal.TEN));

        assertThatThrownBy(() -> pedidoService.atualizar(pedido.getId(), TENANT_ID, USER_ID, dados, itens))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoAtualizarSemItens() {
        Pedido pedido = pedidoComTenant(Pedido.builder().id(UUID.randomUUID())
                .status(StatusPedido.ORCAMENTO).clienteId(CLIENTE_ID).build());
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        Pedido dados = Pedido.builder().clienteId(CLIENTE_ID).build();

        assertThatThrownBy(() -> pedidoService.atualizar(pedido.getId(), TENANT_ID, USER_ID, dados, List.of()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveAtualizarOrcamentoSubstituindoItensERecalculandoTotais() {
        Pedido pedido = pedidoComTenant(Pedido.builder().id(UUID.randomUUID())
                .status(StatusPedido.ORCAMENTO).clienteId(CLIENTE_ID).build());
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        Pedido dados = Pedido.builder().clienteId(CLIENTE_ID).build();
        PedidoItem novoItem = item(new BigDecimal("3"), new BigDecimal("15.00"));

        Pedido resultado = pedidoService.atualizar(pedido.getId(), TENANT_ID, USER_ID, dados, List.of(novoItem));

        assertThat(resultado.getValorTotal()).isEqualByComparingTo("45.00");
        assertThat(resultado.getDataEmissao()).isEqualTo(LocalDate.now());
    }

    @Test
    void deveLancarAoRecalcularPrecosDePedidoQueNaoEstaEmOrcamento() {
        Pedido pedido = pedidoComTenant(Pedido.builder().id(UUID.randomUUID())
                .status(StatusPedido.CONFIRMADO).build());
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoService.recalcularPrecos(pedido.getId(), TENANT_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void recalcularPrecosDeveSerNoOpParaOrcamento() {
        Pedido pedido = pedidoComTenant(Pedido.builder().id(UUID.randomUUID())
                .status(StatusPedido.ORCAMENTO).valorTotal(new BigDecimal("20.00")).build());
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        Pedido resultado = pedidoService.recalcularPrecos(pedido.getId(), TENANT_ID);

        assertThat(resultado.getValorTotal()).isEqualByComparingTo("20.00");
    }
}
