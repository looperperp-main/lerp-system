package com.l.erp.operacoesservice.services.compras;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.operacoesservice.domain.compras.PedidoCompra;
import com.l.erp.operacoesservice.domain.compras.PedidoCompraItem;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusPedidoCompra;
import com.l.erp.operacoesservice.domain.compras.enumerators.TipoDocumentoCompra;
import com.l.erp.operacoesservice.infra.client.CadastroServiceClient;
import com.l.erp.operacoesservice.repository.compras.CompraStatusHistoricoRepository;
import com.l.erp.operacoesservice.repository.compras.PedidoCompraItemRepository;
import com.l.erp.operacoesservice.repository.compras.PedidoCompraRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cobre PedidoCompraService: CRUD, numeração e máquina de estados (spec/p2p-compras.md, Fase 2),
 * mesmo padrão de RequisicaoCompraServiceTest (Fase 1b). */
@ExtendWith(MockitoExtension.class)
class PedidoCompraServiceTest {

    @Mock
    private PedidoCompraRepository pedidoCompraRepository;
    @Mock
    private PedidoCompraItemRepository pedidoCompraItemRepository;
    @Mock
    private CompraStatusHistoricoRepository compraStatusHistoricoRepository;
    @Mock
    private CompraNumeroService compraNumeroService;
    @Mock
    private CadastroServiceClient cadastroServiceClient;

    @InjectMocks
    private PedidoCompraService pedidoCompraService;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    private PedidoCompraItem item() {
        return PedidoCompraItem.builder().produtoId(UUID.randomUUID())
                .quantidade(BigDecimal.ONE).precoUnitario(BigDecimal.TEN).build();
    }

    private PedidoCompra pedidoComTenant(PedidoCompra pedido) {
        pedido.setTenantId(TENANT_ID);
        return pedido;
    }

    private PedidoCompra dadosValidos() {
        return PedidoCompra.builder()
                .fornecedorId(UUID.randomUUID())
                .condicaoPagamentoId(UUID.randomUUID())
                .depositoId(UUID.randomUUID())
                .build();
    }

    private void mockFornecedorAtivo() {
        lenient().when(cadastroServiceClient.buscarFornecedor(any(), any(), any()))
                .thenReturn(new CadastroServiceClient.FornecedorRef(UUID.randomUUID(), "Fornecedor Teste", true));
        lenient().when(cadastroServiceClient.buscarPrecosCusto(any(), any(), any(), any()))
                .thenReturn(Map.of());
    }

    // ---------------------------------------------------------------- criar

    @Test
    void deveLancarAoCriarSemItens() {
        assertThatThrownBy(() -> pedidoCompraService.criar(dadosValidos(), List.of(), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoCriarSemCondicaoPagamento() {
        PedidoCompra dados = PedidoCompra.builder().fornecedorId(UUID.randomUUID()).depositoId(UUID.randomUUID()).build();

        assertThatThrownBy(() -> pedidoCompraService.criar(dados, List.of(item()), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoCriarComDataPrevisaoNoPassado() {
        PedidoCompra dados = dadosValidos();
        dados.setDataPrevisaoEntrega(LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> pedidoCompraService.criar(dados, List.of(item()), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoCriarComFornecedorInativo() {
        when(cadastroServiceClient.buscarFornecedor(any(), any(), any()))
                .thenReturn(new CadastroServiceClient.FornecedorRef(UUID.randomUUID(), "Fornecedor Inativo", false));

        assertThatThrownBy(() -> pedidoCompraService.criar(dadosValidos(), List.of(item()), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveCriarPedidoEmRascunhoComNumeroEValoresRecalculados() {
        mockFornecedorAtivo();
        PedidoCompra dados = dadosValidos();
        PedidoCompraItem item = item();
        when(compraNumeroService.proximoNumero(TENANT_ID, TipoDocumentoCompra.PEDIDO)).thenReturn(1L);
        when(pedidoCompraRepository.save(any(PedidoCompra.class))).thenAnswer(inv -> inv.getArgument(0));

        PedidoCompra salvo = pedidoCompraService.criar(dados, List.of(item), TENANT_ID, USER_ID);

        assertThat(salvo.getStatus()).isEqualTo(StatusPedidoCompra.RASCUNHO);
        assertThat(salvo.getNumero()).isEqualTo(1L);
        assertThat(salvo.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(salvo.getValorTotal()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(item.getValorTotal()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(item.getPedido()).isEqualTo(salvo);
        verify(pedidoCompraItemRepository).saveAll(List.of(item));
        verify(compraStatusHistoricoRepository).save(any());
    }

    @Test
    void deveSomarFreteAoValorTotalNaCriacao() {
        mockFornecedorAtivo();
        PedidoCompra dados = dadosValidos();
        dados.setValorFrete(new BigDecimal("5.00"));
        when(compraNumeroService.proximoNumero(TENANT_ID, TipoDocumentoCompra.PEDIDO)).thenReturn(1L);
        when(pedidoCompraRepository.save(any(PedidoCompra.class))).thenAnswer(inv -> inv.getArgument(0));

        PedidoCompra salvo = pedidoCompraService.criar(dados, List.of(item()), TENANT_ID, USER_ID);

        assertThat(salvo.getValorTotal()).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    // ---------------------------------------------------------------- atualizar

    @Test
    void deveLancarAoAtualizarPedidoQueNaoEhRascunho() {
        PedidoCompra pedido = pedidoComTenant(PedidoCompra.builder().id(UUID.randomUUID())
                .status(StatusPedidoCompra.PENDENTE_APROVACAO).build());
        when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoCompraService.atualizar(pedido.getId(), TENANT_ID, USER_ID,
                dadosValidos(), List.of(item())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveAtualizarSubstituindoItens() {
        mockFornecedorAtivo();
        PedidoCompra pedido = pedidoComTenant(PedidoCompra.builder().id(UUID.randomUUID())
                .status(StatusPedidoCompra.RASCUNHO).build());
        when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));
        when(pedidoCompraRepository.save(any(PedidoCompra.class))).thenAnswer(inv -> inv.getArgument(0));
        PedidoCompraItem novoItem = item();
        PedidoCompra dados = dadosValidos();
        dados.setObservacao("nova observação");

        PedidoCompra atualizado = pedidoCompraService.atualizar(pedido.getId(), TENANT_ID, USER_ID, dados, List.of(novoItem));

        assertThat(atualizado.getObservacao()).isEqualTo("nova observação");
        verify(pedidoCompraItemRepository).deleteAllByPedidoId(pedido.getId());
        verify(pedidoCompraItemRepository).saveAll(List.of(novoItem));
    }

    // ---------------------------------------------------------------- vínculo com cotação (Fase 5)

    @Test
    void deveVincularCotacaoFornecedorAoPedido() {
        PedidoCompra pedido = pedidoComTenant(PedidoCompra.builder().id(UUID.randomUUID())
                .status(StatusPedidoCompra.RASCUNHO).build());
        UUID cotacaoFornecedorId = UUID.randomUUID();
        when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));
        when(pedidoCompraRepository.save(any(PedidoCompra.class))).thenAnswer(inv -> inv.getArgument(0));

        PedidoCompra resultado = pedidoCompraService.vincularCotacaoFornecedor(pedido.getId(), cotacaoFornecedorId, TENANT_ID);

        assertThat(resultado.getCotacaoFornecedorId()).isEqualTo(cotacaoFornecedorId);
    }

    // ---------------------------------------------------------------- transições

    private PedidoCompra pedidoComStatus(StatusPedidoCompra status) {
        return pedidoComTenant(PedidoCompra.builder().id(UUID.randomUUID()).status(status).build());
    }

    @Test
    void deveEnviarParaAprovacaoDeRascunho() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.RASCUNHO);
        when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        PedidoCompra resultado = pedidoCompraService.enviarParaAprovacao(pedido.getId(), TENANT_ID, USER_ID);

        assertThat(resultado.getStatus()).isEqualTo(StatusPedidoCompra.PENDENTE_APROVACAO);
        verify(compraStatusHistoricoRepository).save(any());
    }

    @Test
    void deveLancarAoEnviarParaAprovacaoDeStatusInvalido() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.APROVADO);
        when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoCompraService.enviarParaAprovacao(pedido.getId(), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
        verify(pedidoCompraRepository, never()).save(any());
    }

    @Test
    void deveAprovarDePendenteAprovacaoERegistrarAprovador() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.PENDENTE_APROVACAO);
        when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        PedidoCompra resultado = pedidoCompraService.aprovar(pedido.getId(), TENANT_ID, USER_ID);

        assertThat(resultado.getStatus()).isEqualTo(StatusPedidoCompra.APROVADO);
        assertThat(resultado.getAprovadorId()).isEqualTo(USER_ID);
        assertThat(resultado.getAprovadoEm()).isNotNull();
    }

    @Test
    void deveLancarAoAprovarDeStatusInvalido() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.RASCUNHO);
        when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoCompraService.aprovar(pedido.getId(), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveReprovarDePendenteAprovacaoComMotivo() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.PENDENTE_APROVACAO);
        when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        PedidoCompra resultado = pedidoCompraService.reprovar(pedido.getId(), TENANT_ID, USER_ID, "preço acima do mercado");

        assertThat(resultado.getStatus()).isEqualTo(StatusPedidoCompra.REPROVADO);
    }

    @Test
    void deveLancarAoReprovarSemMotivo() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.PENDENTE_APROVACAO);
        lenient().when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoCompraService.reprovar(pedido.getId(), TENANT_ID, USER_ID, " "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveReabrirDeReprovado() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.REPROVADO);
        when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        PedidoCompra resultado = pedidoCompraService.reabrir(pedido.getId(), TENANT_ID, USER_ID);

        assertThat(resultado.getStatus()).isEqualTo(StatusPedidoCompra.RASCUNHO);
    }

    @Test
    void deveLancarAoReabrirDeStatusQueNaoEhReprovado() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.RASCUNHO);
        when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoCompraService.reabrir(pedido.getId(), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveEnviarDeAprovadoESetarDataEmissao() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.APROVADO);
        when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        PedidoCompra resultado = pedidoCompraService.enviar(pedido.getId(), TENANT_ID, USER_ID);

        assertThat(resultado.getStatus()).isEqualTo(StatusPedidoCompra.ENVIADO);
        assertThat(resultado.getDataEmissao()).isEqualTo(LocalDate.now());
    }

    @Test
    void deveLancarAoEnviarDeStatusInvalido() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.RASCUNHO);
        when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoCompraService.enviar(pedido.getId(), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveCancelarComMotivoAPartirDeRascunho() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.RASCUNHO);
        when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        PedidoCompra resultado = pedidoCompraService.cancelar(pedido.getId(), TENANT_ID, USER_ID, "desistência");

        assertThat(resultado.getStatus()).isEqualTo(StatusPedidoCompra.CANCELADO);
        assertThat(resultado.getMotivoCancelamento()).isEqualTo("desistência");
    }

    @Test
    void deveLancarAoCancelarSemMotivo() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.RASCUNHO);
        lenient().when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoCompraService.cancelar(pedido.getId(), TENANT_ID, USER_ID, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoCancelarPedidoJaEncerrado() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.ENCERRADO);
        when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoCompraService.cancelar(pedido.getId(), TENANT_ID, USER_ID, "motivo qualquer"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoOperarPedidoDeOutroTenant() {
        UUID id = UUID.randomUUID();
        when(pedidoCompraRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pedidoCompraService.buscarPorId(id, TENANT_ID))
                .isInstanceOf(BusinessException.class);
    }

    // ---------------------------------------------------------------- recalcularStatusAposRecebimento (Fase 3)

    private PedidoCompraItem itemComRecebido(BigDecimal quantidade, BigDecimal quantidadeRecebida) {
        return PedidoCompraItem.builder().produtoId(UUID.randomUUID())
                .quantidade(quantidade).precoUnitario(BigDecimal.TEN)
                .quantidadeRecebida(quantidadeRecebida).build();
    }

    @Test
    void deveRecalcularParaRecebidoParcialQuandoAlgumItemIncompleto() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.ENVIADO);
        when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));
        when(pedidoCompraItemRepository.findAllByPedidoId(pedido.getId())).thenReturn(List.of(
                itemComRecebido(BigDecimal.TEN, BigDecimal.ONE),
                itemComRecebido(BigDecimal.TEN, BigDecimal.ZERO)));

        PedidoCompra resultado = pedidoCompraService.recalcularStatusAposRecebimento(pedido.getId(), TENANT_ID, USER_ID);

        assertThat(resultado.getStatus()).isEqualTo(StatusPedidoCompra.RECEBIDO_PARCIAL);
        verify(compraStatusHistoricoRepository).save(any());
    }

    @Test
    void deveRecalcularParaRecebidoTotalQuandoTodosItensCompletos() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.RECEBIDO_PARCIAL);
        when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));
        when(pedidoCompraItemRepository.findAllByPedidoId(pedido.getId())).thenReturn(List.of(
                itemComRecebido(BigDecimal.TEN, BigDecimal.TEN)));

        PedidoCompra resultado = pedidoCompraService.recalcularStatusAposRecebimento(pedido.getId(), TENANT_ID, USER_ID);

        assertThat(resultado.getStatus()).isEqualTo(StatusPedidoCompra.RECEBIDO_TOTAL);
    }

    @Test
    void naoDeveAlterarStatusQuandoRecalculoNaoMuda() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.RECEBIDO_PARCIAL);
        when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));
        when(pedidoCompraItemRepository.findAllByPedidoId(pedido.getId())).thenReturn(List.of(
                itemComRecebido(BigDecimal.TEN, BigDecimal.ONE)));

        PedidoCompra resultado = pedidoCompraService.recalcularStatusAposRecebimento(pedido.getId(), TENANT_ID, USER_ID);

        assertThat(resultado.getStatus()).isEqualTo(StatusPedidoCompra.RECEBIDO_PARCIAL);
        verify(pedidoCompraRepository, never()).save(any());
        verify(compraStatusHistoricoRepository, never()).save(any());
    }

    @Test
    void deveRecalcularDeRecebidoTotalParaEnviadoQuandoCancelamentoZeraTudo() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.RECEBIDO_TOTAL);
        when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));
        when(pedidoCompraItemRepository.findAllByPedidoId(pedido.getId())).thenReturn(List.of(
                itemComRecebido(BigDecimal.TEN, BigDecimal.ZERO)));

        PedidoCompra resultado = pedidoCompraService.recalcularStatusAposRecebimento(pedido.getId(), TENANT_ID, USER_ID);

        assertThat(resultado.getStatus()).isEqualTo(StatusPedidoCompra.ENVIADO);
    }

    // ---------------------------------------------------------------- encerrarSaldo (Fase 3)

    @Test
    void deveEncerrarSaldoDeRecebidoParcialComMotivo() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.RECEBIDO_PARCIAL);
        when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        PedidoCompra resultado = pedidoCompraService.encerrarSaldo(pedido.getId(), TENANT_ID, USER_ID, "cliente não vai receber o restante");

        assertThat(resultado.getStatus()).isEqualTo(StatusPedidoCompra.ENCERRADO);
        verify(compraStatusHistoricoRepository).save(any());
    }

    @Test
    void deveLancarAoEncerrarSaldoSemMotivo() {
        assertThatThrownBy(() -> pedidoCompraService.encerrarSaldo(UUID.randomUUID(), TENANT_ID, USER_ID, " "))
                .isInstanceOf(BusinessException.class);
        verify(pedidoCompraRepository, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void deveLancarAoEncerrarSaldoDeStatusInvalido() {
        PedidoCompra pedido = pedidoComStatus(StatusPedidoCompra.ENVIADO);
        when(pedidoCompraRepository.findByIdAndTenantId(pedido.getId(), TENANT_ID)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoCompraService.encerrarSaldo(pedido.getId(), TENANT_ID, USER_ID, "motivo qualquer"))
                .isInstanceOf(BusinessException.class);
    }
}
