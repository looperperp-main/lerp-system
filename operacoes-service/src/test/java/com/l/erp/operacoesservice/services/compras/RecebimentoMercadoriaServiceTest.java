package com.l.erp.operacoesservice.services.compras;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.operacoesservice.api.dto.RecebimentoMercadoriaItemRequestDTO;
import com.l.erp.operacoesservice.domain.compras.PedidoCompra;
import com.l.erp.operacoesservice.domain.compras.PedidoCompraItem;
import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoria;
import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoriaItem;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusPedidoCompra;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusRecebimentoMercadoria;
import com.l.erp.operacoesservice.domain.compras.enumerators.TipoDocumentoCompra;
import com.l.erp.operacoesservice.domain.compras.enumerators.TipoDocumentoFiscal;
import com.l.erp.operacoesservice.infra.client.CadastroServiceClient;
import com.l.erp.operacoesservice.repository.compras.CompraStatusHistoricoRepository;
import com.l.erp.operacoesservice.repository.compras.PedidoCompraItemRepository;
import com.l.erp.operacoesservice.repository.compras.RecebimentoMercadoriaItemRepository;
import com.l.erp.operacoesservice.repository.compras.RecebimentoMercadoriaRepository;
import com.l.erp.operacoesservice.services.estoque.EstoqueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cobre RecebimentoMercadoriaService: RN-P2P-05 (tolerância), RN-P2P-11 (item de serviço),
 * integração com estoque e máquina de estados (spec/p2p-compras.md, Fase 3). */
@ExtendWith(MockitoExtension.class)
class RecebimentoMercadoriaServiceTest {

    @Mock private RecebimentoMercadoriaRepository recebimentoMercadoriaRepository;
    @Mock private RecebimentoMercadoriaItemRepository recebimentoMercadoriaItemRepository;
    @Mock private PedidoCompraItemRepository pedidoCompraItemRepository;
    @Mock private CompraStatusHistoricoRepository compraStatusHistoricoRepository;
    @Mock private CompraNumeroService compraNumeroService;
    @Mock private PedidoCompraService pedidoCompraService;
    @Mock private EstoqueService estoqueService;
    @Mock private CadastroServiceClient cadastroServiceClient;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RecebimentoMercadoriaService service;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    private PedidoCompra pedidoComStatus(StatusPedidoCompra status) {
        PedidoCompra pedido = PedidoCompra.builder()
                .id(UUID.randomUUID())
                .fornecedorId(UUID.randomUUID())
                .condicaoPagamentoId(UUID.randomUUID())
                .depositoId(UUID.randomUUID())
                .status(status)
                .build();
        pedido.setTenantId(TENANT_ID);
        return pedido;
    }

    private PedidoCompraItem pedidoItem(PedidoCompra pedido, BigDecimal quantidade, BigDecimal quantidadeRecebida) {
        PedidoCompraItem item = PedidoCompraItem.builder()
                .id(UUID.randomUUID())
                .pedido(pedido)
                .produtoId(UUID.randomUUID())
                .quantidade(quantidade)
                .precoUnitario(BigDecimal.TEN)
                .quantidadeRecebida(quantidadeRecebida)
                .build();
        item.setTenantId(TENANT_ID);
        return item;
    }

    private RecebimentoMercadoria dadosValidos() {
        return RecebimentoMercadoria.builder()
                .dataRecebimento(LocalDate.now())
                .tipoDocumentoFiscal(TipoDocumentoFiscal.NFE)
                .nfeNumero("123")
                .nfeSerie("1")
                .nfeChave("35250612345678000195550010000001231234567890")
                .nfeDataEmissao(LocalDate.now())
                .valorTotalNf(BigDecimal.TEN)
                .build();
    }

    private CadastroServiceClient.ProdutoRef produtoMercadoria() {
        return new CadastroServiceClient.ProdutoRef("MERCADORIA", null, true, null, null, "Produto A");
    }

    private CadastroServiceClient.ProdutoRef produtoServico(String codigoServico) {
        return new CadastroServiceClient.ProdutoRef("SERVICO", codigoServico, true, null, null, "Serviço A");
    }

    // ---------------------------------------------------------------- criar()

    @Test
    void deveLancarAoCriarParaPedidoComStatusInvalido() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.RASCUNHO);
        when(pedidoCompraService.buscarPorId(pedido.getId(), TENANT_ID)).thenReturn(pedido);

        assertThatThrownBy(() -> service.criar(pedido.getId(), dadosValidos(), List.of(), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoCriarSemItens() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.ENVIADO);
        when(pedidoCompraService.buscarPorId(pedido.getId(), TENANT_ID)).thenReturn(pedido);

        assertThatThrownBy(() -> service.criar(pedido.getId(), dadosValidos(), List.of(), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoCriarComItemDeOutroPedido() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.ENVIADO);
        PedidoCompra outroPedido = pedidoComStatus(StatusPedidoCompra.ENVIADO);
        PedidoCompraItem itemDeOutroPedido = pedidoItem(outroPedido, BigDecimal.TEN, BigDecimal.ZERO);
        when(pedidoCompraService.buscarPorId(pedido.getId(), TENANT_ID)).thenReturn(pedido);
        when(pedidoCompraItemRepository.findById(itemDeOutroPedido.getId())).thenReturn(Optional.of(itemDeOutroPedido));

        RecebimentoMercadoriaItemRequestDTO itemDto = new RecebimentoMercadoriaItemRequestDTO(
                itemDeOutroPedido.getId(), BigDecimal.ONE, BigDecimal.TEN);

        assertThatThrownBy(() -> service.criar(pedido.getId(), dadosValidos(), List.of(itemDto), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoCriarComItemServicoSemCodigo() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.ENVIADO);
        PedidoCompraItem item = pedidoItem(pedido, BigDecimal.TEN, BigDecimal.ZERO);
        when(pedidoCompraService.buscarPorId(pedido.getId(), TENANT_ID)).thenReturn(pedido);
        when(pedidoCompraItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(cadastroServiceClient.buscarProduto(item.getProdutoId(), TENANT_ID, USER_ID))
                .thenReturn(produtoServico(null));

        RecebimentoMercadoriaItemRequestDTO itemDto = new RecebimentoMercadoriaItemRequestDTO(
                item.getId(), BigDecimal.ONE, BigDecimal.TEN);

        assertThatThrownBy(() -> service.criar(pedido.getId(), dadosValidos(), List.of(itemDto), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoCriarComDadosFiscaisInconsistentes() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.ENVIADO);
        PedidoCompraItem item = pedidoItem(pedido, BigDecimal.TEN, BigDecimal.ZERO);
        when(pedidoCompraService.buscarPorId(pedido.getId(), TENANT_ID)).thenReturn(pedido);

        RecebimentoMercadoria dadosSemChave = RecebimentoMercadoria.builder()
                .dataRecebimento(LocalDate.now())
                .tipoDocumentoFiscal(TipoDocumentoFiscal.NFE)
                .nfeNumero("123")
                .nfeDataEmissao(LocalDate.now())
                .valorTotalNf(BigDecimal.TEN)
                .build();
        RecebimentoMercadoriaItemRequestDTO itemDto = new RecebimentoMercadoriaItemRequestDTO(
                item.getId(), BigDecimal.ONE, BigDecimal.TEN);

        assertThatThrownBy(() -> service.criar(pedido.getId(), dadosSemChave, List.of(itemDto), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveCriarRecebimentoEmConferenciaComNumeroEDepositoDoPedido() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.ENVIADO);
        PedidoCompraItem item = pedidoItem(pedido, BigDecimal.TEN, BigDecimal.ZERO);
        when(pedidoCompraService.buscarPorId(pedido.getId(), TENANT_ID)).thenReturn(pedido);
        when(pedidoCompraItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(cadastroServiceClient.buscarProduto(item.getProdutoId(), TENANT_ID, USER_ID))
                .thenReturn(produtoMercadoria());
        when(compraNumeroService.proximoNumero(TENANT_ID, TipoDocumentoCompra.RECEBIMENTO)).thenReturn(1L);
        when(recebimentoMercadoriaRepository.save(any(RecebimentoMercadoria.class))).thenAnswer(inv -> inv.getArgument(0));

        RecebimentoMercadoriaItemRequestDTO itemDto = new RecebimentoMercadoriaItemRequestDTO(
                item.getId(), BigDecimal.ONE, BigDecimal.TEN);

        RecebimentoMercadoria salvo = service.criar(pedido.getId(), dadosValidos(), List.of(itemDto), TENANT_ID, USER_ID);

        assertThat(salvo.getStatus()).isEqualTo(StatusRecebimentoMercadoria.EM_CONFERENCIA);
        assertThat(salvo.getNumero()).isEqualTo(1L);
        assertThat(salvo.getDepositoId()).isEqualTo(pedido.getDepositoId());
        assertThat(salvo.getCondicaoPagamentoId()).isEqualTo(pedido.getCondicaoPagamentoId());
        verify(recebimentoMercadoriaItemRepository).saveAll(any());
        verify(compraStatusHistoricoRepository).save(any());
    }

    // ---------------------------------------------------------------- confirmar()

    @Test
    void deveLancarAoConfirmarQuandoNaoEmConferencia() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.ENVIADO);
        RecebimentoMercadoria recebimento = RecebimentoMercadoria.builder().id(UUID.randomUUID())
                .pedido(pedido).status(StatusRecebimentoMercadoria.CONFIRMADO).build();
        recebimento.setTenantId(TENANT_ID);
        when(recebimentoMercadoriaRepository.findByIdAndTenantId(recebimento.getId(), TENANT_ID))
                .thenReturn(Optional.of(recebimento));

        assertThatThrownBy(() -> service.confirmar(recebimento.getId(), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveConfirmarEBaixarEstoqueParaItemMercadoriaERecalcularPedido() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.ENVIADO);
        PedidoCompraItem pedidoItem = pedidoItem(pedido, BigDecimal.TEN, BigDecimal.ZERO);
        RecebimentoMercadoria recebimento = RecebimentoMercadoria.builder().id(UUID.randomUUID())
                .pedido(pedido).status(StatusRecebimentoMercadoria.EM_CONFERENCIA)
                .depositoId(pedido.getDepositoId()).build();
        recebimento.setTenantId(TENANT_ID);
        RecebimentoMercadoriaItem item = RecebimentoMercadoriaItem.builder()
                .pedidoItem(pedidoItem).quantidade(BigDecimal.ONE).precoUnitarioNf(BigDecimal.TEN).build();

        when(recebimentoMercadoriaRepository.findByIdAndTenantId(recebimento.getId(), TENANT_ID))
                .thenReturn(Optional.of(recebimento));
        when(recebimentoMercadoriaItemRepository.findAllByRecebimentoId(recebimento.getId()))
                .thenReturn(List.of(item));
        when(cadastroServiceClient.buscarProduto(pedidoItem.getProdutoId(), TENANT_ID, USER_ID))
                .thenReturn(produtoMercadoria());

        RecebimentoMercadoria resultado = service.confirmar(recebimento.getId(), TENANT_ID, USER_ID);

        assertThat(resultado.getStatus()).isEqualTo(StatusRecebimentoMercadoria.CONFIRMADO);
        assertThat(pedidoItem.getQuantidadeRecebida()).isEqualByComparingTo(BigDecimal.ONE);
        verify(estoqueService).registrarMovimento(any());
        verify(pedidoCompraService).recalcularStatusAposRecebimento(pedido.getId(), TENANT_ID, USER_ID);
        verify(compraStatusHistoricoRepository).save(any());
        verify(eventPublisher).publishEvent(any(RecebimentoConfirmadoEvent.class));
    }

    @Test
    void naoDeveBaixarEstoqueParaItemDeServico() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.ENVIADO);
        PedidoCompraItem pedidoItem = pedidoItem(pedido, BigDecimal.TEN, BigDecimal.ZERO);
        RecebimentoMercadoria recebimento = RecebimentoMercadoria.builder().id(UUID.randomUUID())
                .pedido(pedido).status(StatusRecebimentoMercadoria.EM_CONFERENCIA).build();
        recebimento.setTenantId(TENANT_ID);
        RecebimentoMercadoriaItem item = RecebimentoMercadoriaItem.builder()
                .pedidoItem(pedidoItem).quantidade(BigDecimal.ONE).precoUnitarioNf(BigDecimal.TEN).build();

        when(recebimentoMercadoriaRepository.findByIdAndTenantId(recebimento.getId(), TENANT_ID))
                .thenReturn(Optional.of(recebimento));
        when(recebimentoMercadoriaItemRepository.findAllByRecebimentoId(recebimento.getId()))
                .thenReturn(List.of(item));
        when(cadastroServiceClient.buscarProduto(pedidoItem.getProdutoId(), TENANT_ID, USER_ID))
                .thenReturn(produtoServico("SRV-01"));

        service.confirmar(recebimento.getId(), TENANT_ID, USER_ID);

        verify(estoqueService, never()).registrarMovimento(any());
    }

    @Test
    void deveLancarAoConfirmarQuandoExcedeToleranciaDeCincoPorCento() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.ENVIADO);
        // quantidade pedida = 10, limite com 5% de tolerância = 10.5; tentando receber 11 -> estoura
        PedidoCompraItem pedidoItem = pedidoItem(pedido, BigDecimal.TEN, BigDecimal.ZERO);
        RecebimentoMercadoria recebimento = RecebimentoMercadoria.builder().id(UUID.randomUUID())
                .pedido(pedido).status(StatusRecebimentoMercadoria.EM_CONFERENCIA).build();
        recebimento.setTenantId(TENANT_ID);
        RecebimentoMercadoriaItem item = RecebimentoMercadoriaItem.builder()
                .pedidoItem(pedidoItem).quantidade(new BigDecimal("11")).precoUnitarioNf(BigDecimal.TEN).build();

        when(recebimentoMercadoriaRepository.findByIdAndTenantId(recebimento.getId(), TENANT_ID))
                .thenReturn(Optional.of(recebimento));
        when(recebimentoMercadoriaItemRepository.findAllByRecebimentoId(recebimento.getId()))
                .thenReturn(List.of(item));
        lenient().when(cadastroServiceClient.buscarProduto(pedidoItem.getProdutoId(), TENANT_ID, USER_ID))
                .thenReturn(produtoMercadoria());

        assertThatThrownBy(() -> service.confirmar(recebimento.getId(), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
        verify(estoqueService, never()).registrarMovimento(any());
    }

    // ---------------------------------------------------------------- cancelar()

    @Test
    void deveCancelarEmConferenciaSemTocarEstoque() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.ENVIADO);
        RecebimentoMercadoria recebimento = RecebimentoMercadoria.builder().id(UUID.randomUUID())
                .pedido(pedido).status(StatusRecebimentoMercadoria.EM_CONFERENCIA).build();
        recebimento.setTenantId(TENANT_ID);
        when(recebimentoMercadoriaRepository.findByIdAndTenantId(recebimento.getId(), TENANT_ID))
                .thenReturn(Optional.of(recebimento));

        RecebimentoMercadoria resultado = service.cancelar(recebimento.getId(), TENANT_ID, USER_ID, "desistência");

        assertThat(resultado.getStatus()).isEqualTo(StatusRecebimentoMercadoria.CANCELADO);
        assertThat(resultado.getMotivoCancelamento()).isEqualTo("desistência");
        verify(estoqueService, never()).registrarMovimento(any());
        verify(pedidoCompraService, never()).recalcularStatusAposRecebimento(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any(RecebimentoCanceladoEvent.class));
    }

    @Test
    void deveCancelarConfirmadoEEstornarEstoqueEDevolverQuantidade() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.RECEBIDO_PARCIAL);
        PedidoCompraItem pedidoItem = pedidoItem(pedido, BigDecimal.TEN, BigDecimal.ONE);
        RecebimentoMercadoria recebimento = RecebimentoMercadoria.builder().id(UUID.randomUUID())
                .pedido(pedido).status(StatusRecebimentoMercadoria.CONFIRMADO)
                .depositoId(pedido.getDepositoId()).build();
        recebimento.setTenantId(TENANT_ID);
        RecebimentoMercadoriaItem item = RecebimentoMercadoriaItem.builder()
                .pedidoItem(pedidoItem).quantidade(BigDecimal.ONE).precoUnitarioNf(BigDecimal.TEN).build();

        when(recebimentoMercadoriaRepository.findByIdAndTenantId(recebimento.getId(), TENANT_ID))
                .thenReturn(Optional.of(recebimento));
        when(recebimentoMercadoriaItemRepository.findAllByRecebimentoId(recebimento.getId()))
                .thenReturn(List.of(item));
        when(cadastroServiceClient.buscarProduto(pedidoItem.getProdutoId(), TENANT_ID, USER_ID))
                .thenReturn(produtoMercadoria());

        RecebimentoMercadoria resultado = service.cancelar(recebimento.getId(), TENANT_ID, USER_ID, "NF cancelada");

        assertThat(resultado.getStatus()).isEqualTo(StatusRecebimentoMercadoria.CANCELADO);
        assertThat(pedidoItem.getQuantidadeRecebida()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(estoqueService).registrarMovimento(any());
        verify(pedidoCompraService).recalcularStatusAposRecebimento(pedido.getId(), TENANT_ID, USER_ID);
        verify(eventPublisher).publishEvent(any(RecebimentoCanceladoEvent.class));
    }

    @Test
    void deveLancarAoCancelarRecebimentoJaCancelado() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.ENVIADO);
        RecebimentoMercadoria recebimento = RecebimentoMercadoria.builder().id(UUID.randomUUID())
                .pedido(pedido).status(StatusRecebimentoMercadoria.CANCELADO).build();
        recebimento.setTenantId(TENANT_ID);
        when(recebimentoMercadoriaRepository.findByIdAndTenantId(recebimento.getId(), TENANT_ID))
                .thenReturn(Optional.of(recebimento));

        assertThatThrownBy(() -> service.cancelar(recebimento.getId(), TENANT_ID, USER_ID, "motivo"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoBuscarRecebimentoDeOutroTenant() {
        UUID id = UUID.randomUUID();
        when(recebimentoMercadoriaRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(id, TENANT_ID))
                .isInstanceOf(BusinessException.class);
    }
}
