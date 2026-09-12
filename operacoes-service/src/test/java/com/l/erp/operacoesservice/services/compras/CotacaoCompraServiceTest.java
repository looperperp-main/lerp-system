package com.l.erp.operacoesservice.services.compras;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraFornecedorResponseDTO;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraRespostaItemRequestDTO;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraRespostaRequestDTO;
import com.l.erp.operacoesservice.domain.compras.CotacaoCompra;
import com.l.erp.operacoesservice.domain.compras.CotacaoCompraFornecedor;
import com.l.erp.operacoesservice.domain.compras.CotacaoCompraFornecedorItem;
import com.l.erp.operacoesservice.domain.compras.CotacaoCompraItem;
import com.l.erp.operacoesservice.domain.compras.PedidoCompra;
import com.l.erp.operacoesservice.domain.compras.RequisicaoCompra;
import com.l.erp.operacoesservice.domain.compras.RequisicaoCompraItem;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cobre CotacaoCompraService: criação (avulsa/a partir de requisição), resposta/declínio de
 * fornecedor, encerramento (com geração de PedidoCompra) e cancelamento (spec/p2p-compras.md,
 * Fase 5), mesmo padrão de PedidoCompraServiceTest/RequisicaoCompraServiceTest. */
@ExtendWith(MockitoExtension.class)
class CotacaoCompraServiceTest {

    @Mock
    private CotacaoCompraRepository cotacaoCompraRepository;
    @Mock
    private CotacaoCompraItemRepository cotacaoCompraItemRepository;
    @Mock
    private CotacaoCompraFornecedorRepository cotacaoCompraFornecedorRepository;
    @Mock
    private CotacaoCompraFornecedorItemRepository cotacaoCompraFornecedorItemRepository;
    @Mock
    private RequisicaoCompraRepository requisicaoCompraRepository;
    @Mock
    private RequisicaoCompraItemRepository requisicaoCompraItemRepository;
    @Mock
    private CompraStatusHistoricoRepository compraStatusHistoricoRepository;
    @Mock
    private CompraNumeroService compraNumeroService;
    @Mock
    private CadastroServiceClient cadastroServiceClient;
    @Mock
    private RequisicaoCompraService requisicaoCompraService;
    @Mock
    private PedidoCompraService pedidoCompraService;

    @InjectMocks
    private CotacaoCompraService cotacaoCompraService;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    private CotacaoCompra cotacaoAvulsa() {
        return CotacaoCompra.builder().depositoId(UUID.randomUUID()).build();
    }

    private CotacaoCompra cotacaoComStatus(StatusCotacaoCompra status) {
        CotacaoCompra cotacao = CotacaoCompra.builder().id(UUID.randomUUID()).depositoId(UUID.randomUUID())
                .numero(1L).status(status).build();
        cotacao.setTenantId(TENANT_ID);
        return cotacao;
    }

    private CotacaoCompraItem itemCotacao() {
        return CotacaoCompraItem.builder().id(UUID.randomUUID()).produtoId(UUID.randomUUID()).quantidade(BigDecimal.ONE).build();
    }

    private CotacaoCompraFornecedor fornecedorConvite(CotacaoCompra cotacao, StatusCotacaoCompraFornecedor status) {
        CotacaoCompraFornecedor fornecedor = CotacaoCompraFornecedor.builder().id(UUID.randomUUID()).cotacao(cotacao)
                .fornecedorId(UUID.randomUUID()).status(status).valorFrete(BigDecimal.ZERO).build();
        fornecedor.setTenantId(TENANT_ID);
        return fornecedor;
    }

    private void mockFornecedorAtivo() {
        lenient().when(cadastroServiceClient.buscarFornecedor(any(), any(), any()))
                .thenReturn(new CadastroServiceClient.FornecedorRef(UUID.randomUUID(), "Fornecedor Teste", true));
    }

    // ---------------------------------------------------------------- criar

    @Test
    void deveLancarAoCriarSemItens() {
        assertThatThrownBy(() -> cotacaoCompraService.criar(
                cotacaoAvulsa(), List.of(), List.of(UUID.randomUUID()), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoCriarSemFornecedores() {
        assertThatThrownBy(() -> cotacaoCompraService.criar(
                cotacaoAvulsa(), List.of(itemCotacao()), List.of(), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoCriarComFornecedorInativo() {
        when(cadastroServiceClient.buscarFornecedor(any(), any(), any()))
                .thenReturn(new CadastroServiceClient.FornecedorRef(UUID.randomUUID(), "Fornecedor Inativo", false));

        assertThatThrownBy(() -> cotacaoCompraService.criar(
                cotacaoAvulsa(), List.of(itemCotacao()), List.of(UUID.randomUUID()), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoCriarComRequisicaoInexistente() {
        UUID requisicaoId = UUID.randomUUID();
        CotacaoCompra dados = CotacaoCompra.builder().requisicaoId(requisicaoId).depositoId(UUID.randomUUID()).build();
        when(requisicaoCompraRepository.findByIdAndTenantId(requisicaoId, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cotacaoCompraService.criar(dados, List.of(), List.of(UUID.randomUUID()), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveCriarCotacaoAvulsaComNumeroEConvites() {
        mockFornecedorAtivo();
        CotacaoCompraItem item = itemCotacao();
        List<UUID> fornecedores = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(compraNumeroService.proximoNumero(TENANT_ID, TipoDocumentoCompra.COTACAO)).thenReturn(1L);
        when(cotacaoCompraRepository.save(any(CotacaoCompra.class))).thenAnswer(inv -> inv.getArgument(0));

        CotacaoCompra salva = cotacaoCompraService.criar(cotacaoAvulsa(), List.of(item), fornecedores, TENANT_ID, USER_ID);

        assertThat(salva.getStatus()).isEqualTo(StatusCotacaoCompra.ABERTA);
        assertThat(salva.getNumero()).isEqualTo(1L);
        assertThat(salva.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(item.getCotacao()).isEqualTo(salva);
        verify(cotacaoCompraItemRepository).saveAll(List.of(item));
        verify(cotacaoCompraFornecedorRepository, times(2)).save(any(CotacaoCompraFornecedor.class));
        verify(compraStatusHistoricoRepository).save(any());
        verify(requisicaoCompraService, never()).iniciarCotacao(any(), any(), any());
    }

    @Test
    void deveCriarCotacaoAPartirDeRequisicaoCopiandoItensEIniciarCotacao() {
        mockFornecedorAtivo();
        UUID requisicaoId = UUID.randomUUID();
        CotacaoCompra dados = CotacaoCompra.builder().requisicaoId(requisicaoId).depositoId(UUID.randomUUID()).build();
        RequisicaoCompra requisicao = RequisicaoCompra.builder().id(requisicaoId).build();
        RequisicaoCompraItem itemRequisicao = RequisicaoCompraItem.builder().produtoId(UUID.randomUUID()).quantidade(BigDecimal.TEN).build();
        when(requisicaoCompraRepository.findByIdAndTenantId(requisicaoId, TENANT_ID)).thenReturn(Optional.of(requisicao));
        when(requisicaoCompraItemRepository.findAllByRequisicaoId(requisicaoId)).thenReturn(List.of(itemRequisicao));
        when(compraNumeroService.proximoNumero(TENANT_ID, TipoDocumentoCompra.COTACAO)).thenReturn(2L);
        when(cotacaoCompraRepository.save(any(CotacaoCompra.class))).thenAnswer(inv -> inv.getArgument(0));

        cotacaoCompraService.criar(dados, List.of(), List.of(UUID.randomUUID()), TENANT_ID, USER_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CotacaoCompraItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(cotacaoCompraItemRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getProdutoId()).isEqualTo(itemRequisicao.getProdutoId());
        assertThat(captor.getValue().get(0).getQuantidade()).isEqualByComparingTo(BigDecimal.TEN);
        verify(requisicaoCompraService).iniciarCotacao(requisicaoId, TENANT_ID, USER_ID);
    }

    @Test
    void deveIgnorarFornecedoresDuplicadosNaCriacao() {
        mockFornecedorAtivo();
        UUID fornecedorId = UUID.randomUUID();
        when(compraNumeroService.proximoNumero(TENANT_ID, TipoDocumentoCompra.COTACAO)).thenReturn(1L);
        when(cotacaoCompraRepository.save(any(CotacaoCompra.class))).thenAnswer(inv -> inv.getArgument(0));

        cotacaoCompraService.criar(cotacaoAvulsa(), List.of(itemCotacao()), List.of(fornecedorId, fornecedorId), TENANT_ID, USER_ID);

        verify(cotacaoCompraFornecedorRepository, times(1)).save(any(CotacaoCompraFornecedor.class));
        verify(cadastroServiceClient, times(1)).buscarFornecedor(eq(fornecedorId), eq(TENANT_ID), eq(USER_ID));
    }

    // ---------------------------------------------------------------- registrarResposta

    @Test
    void deveLancarAoRegistrarRespostaComCotacaoNaoAberta() {
        CotacaoCompra cotacao = cotacaoComStatus(StatusCotacaoCompra.ENCERRADA);
        when(cotacaoCompraRepository.findByIdAndTenantId(cotacao.getId(), TENANT_ID)).thenReturn(Optional.of(cotacao));
        CotacaoCompraRespostaRequestDTO dto = new CotacaoCompraRespostaRequestDTO(UUID.randomUUID(), 5, BigDecimal.TEN, null, List.of());

        assertThatThrownBy(() -> cotacaoCompraService.registrarResposta(cotacao.getId(), UUID.randomUUID(), TENANT_ID, USER_ID, dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoRegistrarRespostaComFornecedorJaRespondido() {
        CotacaoCompra cotacao = cotacaoComStatus(StatusCotacaoCompra.ABERTA);
        CotacaoCompraFornecedor fornecedor = fornecedorConvite(cotacao, StatusCotacaoCompraFornecedor.RESPONDIDA);
        when(cotacaoCompraRepository.findByIdAndTenantId(cotacao.getId(), TENANT_ID)).thenReturn(Optional.of(cotacao));
        when(cotacaoCompraFornecedorRepository.findByIdAndTenantId(fornecedor.getId(), TENANT_ID)).thenReturn(Optional.of(fornecedor));
        CotacaoCompraRespostaRequestDTO dto = new CotacaoCompraRespostaRequestDTO(UUID.randomUUID(), 5, BigDecimal.TEN, null, List.of());

        assertThatThrownBy(() -> cotacaoCompraService.registrarResposta(cotacao.getId(), fornecedor.getId(), TENANT_ID, USER_ID, dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoRegistrarRespostaSemCondicaoPagamento() {
        CotacaoCompra cotacao = cotacaoComStatus(StatusCotacaoCompra.ABERTA);
        CotacaoCompraFornecedor fornecedor = fornecedorConvite(cotacao, StatusCotacaoCompraFornecedor.AGUARDANDO);
        when(cotacaoCompraRepository.findByIdAndTenantId(cotacao.getId(), TENANT_ID)).thenReturn(Optional.of(cotacao));
        when(cotacaoCompraFornecedorRepository.findByIdAndTenantId(fornecedor.getId(), TENANT_ID)).thenReturn(Optional.of(fornecedor));
        CotacaoCompraRespostaRequestDTO dto = new CotacaoCompraRespostaRequestDTO(null, 5, BigDecimal.TEN, null, List.of());

        assertThatThrownBy(() -> cotacaoCompraService.registrarResposta(cotacao.getId(), fornecedor.getId(), TENANT_ID, USER_ID, dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoRegistrarRespostaComItensDivergentes() {
        CotacaoCompra cotacao = cotacaoComStatus(StatusCotacaoCompra.ABERTA);
        CotacaoCompraFornecedor fornecedor = fornecedorConvite(cotacao, StatusCotacaoCompraFornecedor.AGUARDANDO);
        when(cotacaoCompraRepository.findByIdAndTenantId(cotacao.getId(), TENANT_ID)).thenReturn(Optional.of(cotacao));
        when(cotacaoCompraFornecedorRepository.findByIdAndTenantId(fornecedor.getId(), TENANT_ID)).thenReturn(Optional.of(fornecedor));
        when(cotacaoCompraItemRepository.findAllByCotacaoId(cotacao.getId())).thenReturn(List.of(itemCotacao()));
        CotacaoCompraRespostaRequestDTO dto = new CotacaoCompraRespostaRequestDTO(UUID.randomUUID(), 5, BigDecimal.TEN, null,
                List.of(new CotacaoCompraRespostaItemRequestDTO(UUID.randomUUID(), BigDecimal.TEN)));

        assertThatThrownBy(() -> cotacaoCompraService.registrarResposta(cotacao.getId(), fornecedor.getId(), TENANT_ID, USER_ID, dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveRegistrarRespostaComSucesso() {
        CotacaoCompra cotacao = cotacaoComStatus(StatusCotacaoCompra.ABERTA);
        CotacaoCompraFornecedor fornecedor = fornecedorConvite(cotacao, StatusCotacaoCompraFornecedor.AGUARDANDO);
        CotacaoCompraItem item = itemCotacao();
        when(cotacaoCompraRepository.findByIdAndTenantId(cotacao.getId(), TENANT_ID)).thenReturn(Optional.of(cotacao));
        when(cotacaoCompraFornecedorRepository.findByIdAndTenantId(fornecedor.getId(), TENANT_ID)).thenReturn(Optional.of(fornecedor));
        when(cotacaoCompraItemRepository.findAllByCotacaoId(cotacao.getId())).thenReturn(List.of(item));
        CotacaoCompraRespostaRequestDTO dto = new CotacaoCompraRespostaRequestDTO(UUID.randomUUID(), 5, new BigDecimal("20.00"), "obs",
                List.of(new CotacaoCompraRespostaItemRequestDTO(item.getId(), new BigDecimal("15.00"))));

        cotacaoCompraService.registrarResposta(cotacao.getId(), fornecedor.getId(), TENANT_ID, USER_ID, dto);

        assertThat(fornecedor.getStatus()).isEqualTo(StatusCotacaoCompraFornecedor.RESPONDIDA);
        assertThat(fornecedor.getCondicaoPagamentoId()).isEqualTo(dto.condicaoPagamentoId());
        assertThat(fornecedor.getValorFrete()).isEqualByComparingTo("20.00");
        assertThat(fornecedor.getObservacao()).isEqualTo("obs");
        verify(cotacaoCompraFornecedorItemRepository).deleteAllByCotacaoFornecedorId(fornecedor.getId());
        verify(cotacaoCompraFornecedorItemRepository).saveAll(anyList());
        verify(cotacaoCompraFornecedorRepository).save(fornecedor);
    }

    @Test
    void deveUsarValorFreteZeroQuandoNaoInformadoNaResposta() {
        CotacaoCompra cotacao = cotacaoComStatus(StatusCotacaoCompra.ABERTA);
        CotacaoCompraFornecedor fornecedor = fornecedorConvite(cotacao, StatusCotacaoCompraFornecedor.AGUARDANDO);
        CotacaoCompraItem item = itemCotacao();
        when(cotacaoCompraRepository.findByIdAndTenantId(cotacao.getId(), TENANT_ID)).thenReturn(Optional.of(cotacao));
        when(cotacaoCompraFornecedorRepository.findByIdAndTenantId(fornecedor.getId(), TENANT_ID)).thenReturn(Optional.of(fornecedor));
        when(cotacaoCompraItemRepository.findAllByCotacaoId(cotacao.getId())).thenReturn(List.of(item));
        CotacaoCompraRespostaRequestDTO dto = new CotacaoCompraRespostaRequestDTO(UUID.randomUUID(), null, null, null,
                List.of(new CotacaoCompraRespostaItemRequestDTO(item.getId(), BigDecimal.TEN)));

        cotacaoCompraService.registrarResposta(cotacao.getId(), fornecedor.getId(), TENANT_ID, USER_ID, dto);

        assertThat(fornecedor.getValorFrete()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---------------------------------------------------------------- declinar

    @Test
    void deveLancarAoDeclinarCotacaoNaoAberta() {
        CotacaoCompra cotacao = cotacaoComStatus(StatusCotacaoCompra.CANCELADA);
        when(cotacaoCompraRepository.findByIdAndTenantId(cotacao.getId(), TENANT_ID)).thenReturn(Optional.of(cotacao));

        assertThatThrownBy(() -> cotacaoCompraService.declinar(cotacao.getId(), UUID.randomUUID(), TENANT_ID, USER_ID, "motivo"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoDeclinarFornecedorJaDeclinado() {
        CotacaoCompra cotacao = cotacaoComStatus(StatusCotacaoCompra.ABERTA);
        CotacaoCompraFornecedor fornecedor = fornecedorConvite(cotacao, StatusCotacaoCompraFornecedor.DECLINADA);
        when(cotacaoCompraRepository.findByIdAndTenantId(cotacao.getId(), TENANT_ID)).thenReturn(Optional.of(cotacao));
        when(cotacaoCompraFornecedorRepository.findByIdAndTenantId(fornecedor.getId(), TENANT_ID)).thenReturn(Optional.of(fornecedor));

        assertThatThrownBy(() -> cotacaoCompraService.declinar(cotacao.getId(), fornecedor.getId(), TENANT_ID, USER_ID, "motivo"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveDeclinarComSucesso() {
        CotacaoCompra cotacao = cotacaoComStatus(StatusCotacaoCompra.ABERTA);
        CotacaoCompraFornecedor fornecedor = fornecedorConvite(cotacao, StatusCotacaoCompraFornecedor.AGUARDANDO);
        when(cotacaoCompraRepository.findByIdAndTenantId(cotacao.getId(), TENANT_ID)).thenReturn(Optional.of(cotacao));
        when(cotacaoCompraFornecedorRepository.findByIdAndTenantId(fornecedor.getId(), TENANT_ID)).thenReturn(Optional.of(fornecedor));

        cotacaoCompraService.declinar(cotacao.getId(), fornecedor.getId(), TENANT_ID, USER_ID, "sem estoque");

        assertThat(fornecedor.getStatus()).isEqualTo(StatusCotacaoCompraFornecedor.DECLINADA);
        assertThat(fornecedor.getObservacao()).isEqualTo("sem estoque");
    }

    // ---------------------------------------------------------------- encerrar

    @Test
    void deveLancarAoEncerrarComTransicaoInvalida() {
        CotacaoCompra cotacao = cotacaoComStatus(StatusCotacaoCompra.ENCERRADA);
        when(cotacaoCompraRepository.findByIdAndTenantId(cotacao.getId(), TENANT_ID)).thenReturn(Optional.of(cotacao));

        assertThatThrownBy(() -> cotacaoCompraService.encerrar(cotacao.getId(), UUID.randomUUID(), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoEncerrarComVencedorNaoRespondido() {
        CotacaoCompra cotacao = cotacaoComStatus(StatusCotacaoCompra.ABERTA);
        CotacaoCompraFornecedor fornecedor = fornecedorConvite(cotacao, StatusCotacaoCompraFornecedor.AGUARDANDO);
        when(cotacaoCompraRepository.findByIdAndTenantId(cotacao.getId(), TENANT_ID)).thenReturn(Optional.of(cotacao));
        when(cotacaoCompraFornecedorRepository.findByIdAndTenantId(fornecedor.getId(), TENANT_ID)).thenReturn(Optional.of(fornecedor));

        assertThatThrownBy(() -> cotacaoCompraService.encerrar(cotacao.getId(), fornecedor.getId(), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveEncerrarGerandoPedidoEAtenderRequisicaoQuandoOriginadaDeRequisicao() {
        UUID requisicaoId = UUID.randomUUID();
        CotacaoCompra cotacao = cotacaoComStatus(StatusCotacaoCompra.ABERTA);
        cotacao.setRequisicaoId(requisicaoId);
        CotacaoCompraFornecedor vencedor = fornecedorConvite(cotacao, StatusCotacaoCompraFornecedor.RESPONDIDA);
        vencedor.setCondicaoPagamentoId(UUID.randomUUID());
        vencedor.setPrazoEntregaDias(10);
        CotacaoCompraItem item = itemCotacao();
        CotacaoCompraFornecedorItem itemVencedor = CotacaoCompraFornecedorItem.builder()
                .cotacaoFornecedor(vencedor).cotacaoItem(item).precoUnitario(BigDecimal.TEN).build();
        when(cotacaoCompraRepository.findByIdAndTenantId(cotacao.getId(), TENANT_ID)).thenReturn(Optional.of(cotacao));
        when(cotacaoCompraFornecedorRepository.findByIdAndTenantId(vencedor.getId(), TENANT_ID)).thenReturn(Optional.of(vencedor));
        when(cotacaoCompraFornecedorItemRepository.findAllByCotacaoFornecedorId(vencedor.getId())).thenReturn(List.of(itemVencedor));
        PedidoCompra pedidoGerado = PedidoCompra.builder().id(UUID.randomUUID()).build();
        when(pedidoCompraService.criar(any(PedidoCompra.class), anyList(), eq(TENANT_ID), eq(USER_ID))).thenReturn(pedidoGerado);
        when(cotacaoCompraRepository.save(any(CotacaoCompra.class))).thenAnswer(inv -> inv.getArgument(0));

        CotacaoCompra resultado = cotacaoCompraService.encerrar(cotacao.getId(), vencedor.getId(), TENANT_ID, USER_ID);

        assertThat(resultado.getStatus()).isEqualTo(StatusCotacaoCompra.ENCERRADA);
        assertThat(resultado.getCotacaoFornecedorVencedorId()).isEqualTo(vencedor.getId());
        verify(pedidoCompraService).criar(any(PedidoCompra.class), anyList(), eq(TENANT_ID), eq(USER_ID));
        verify(pedidoCompraService).vincularCotacaoFornecedor(pedidoGerado.getId(), vencedor.getId(), TENANT_ID);
        verify(requisicaoCompraService).atender(requisicaoId, TENANT_ID, USER_ID);
        verify(compraStatusHistoricoRepository).save(any());
    }

    @Test
    void deveEncerrarSemChamarAtenderQuandoAvulsa() {
        CotacaoCompra cotacao = cotacaoComStatus(StatusCotacaoCompra.ABERTA);
        CotacaoCompraFornecedor vencedor = fornecedorConvite(cotacao, StatusCotacaoCompraFornecedor.RESPONDIDA);
        when(cotacaoCompraRepository.findByIdAndTenantId(cotacao.getId(), TENANT_ID)).thenReturn(Optional.of(cotacao));
        when(cotacaoCompraFornecedorRepository.findByIdAndTenantId(vencedor.getId(), TENANT_ID)).thenReturn(Optional.of(vencedor));
        when(cotacaoCompraFornecedorItemRepository.findAllByCotacaoFornecedorId(vencedor.getId())).thenReturn(List.of());
        when(pedidoCompraService.criar(any(PedidoCompra.class), anyList(), eq(TENANT_ID), eq(USER_ID)))
                .thenReturn(PedidoCompra.builder().id(UUID.randomUUID()).build());
        when(cotacaoCompraRepository.save(any(CotacaoCompra.class))).thenAnswer(inv -> inv.getArgument(0));

        cotacaoCompraService.encerrar(cotacao.getId(), vencedor.getId(), TENANT_ID, USER_ID);

        verify(requisicaoCompraService, never()).atender(any(), any(), any());
    }

    // ---------------------------------------------------------------- cancelar

    @Test
    void deveLancarAoCancelarSemMotivo() {
        assertThatThrownBy(() -> cotacaoCompraService.cancelar(UUID.randomUUID(), TENANT_ID, USER_ID, " "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoCancelarComTransicaoInvalida() {
        CotacaoCompra cotacao = cotacaoComStatus(StatusCotacaoCompra.CANCELADA);
        when(cotacaoCompraRepository.findByIdAndTenantId(cotacao.getId(), TENANT_ID)).thenReturn(Optional.of(cotacao));

        assertThatThrownBy(() -> cotacaoCompraService.cancelar(cotacao.getId(), TENANT_ID, USER_ID, "motivo"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveCancelarComSucessoEVoltarRequisicaoParaAprovada() {
        UUID requisicaoId = UUID.randomUUID();
        CotacaoCompra cotacao = cotacaoComStatus(StatusCotacaoCompra.ABERTA);
        cotacao.setRequisicaoId(requisicaoId);
        when(cotacaoCompraRepository.findByIdAndTenantId(cotacao.getId(), TENANT_ID)).thenReturn(Optional.of(cotacao));
        when(cotacaoCompraRepository.save(any(CotacaoCompra.class))).thenAnswer(inv -> inv.getArgument(0));

        CotacaoCompra resultado = cotacaoCompraService.cancelar(cotacao.getId(), TENANT_ID, USER_ID, "desistência");

        assertThat(resultado.getStatus()).isEqualTo(StatusCotacaoCompra.CANCELADA);
        verify(requisicaoCompraService).voltarParaAprovada(requisicaoId, TENANT_ID, USER_ID);
    }

    @Test
    void deveCancelarSemChamarVoltarParaAprovadaQuandoAvulsa() {
        CotacaoCompra cotacao = cotacaoComStatus(StatusCotacaoCompra.ABERTA);
        when(cotacaoCompraRepository.findByIdAndTenantId(cotacao.getId(), TENANT_ID)).thenReturn(Optional.of(cotacao));
        when(cotacaoCompraRepository.save(any(CotacaoCompra.class))).thenAnswer(inv -> inv.getArgument(0));

        cotacaoCompraService.cancelar(cotacao.getId(), TENANT_ID, USER_ID, "desistência");

        verify(requisicaoCompraService, never()).voltarParaAprovada(any(), any(), any());
    }

    // ---------------------------------------------------------------- consultas

    @Test
    void deveLancarAoBuscarCotacaoDeOutroTenant() {
        UUID id = UUID.randomUUID();
        when(cotacaoCompraRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cotacaoCompraService.buscarPorId(id, TENANT_ID))
                .isInstanceOf(BusinessException.class);
    }

    // ---------------------------------------------------------------- ranking (listarFornecedores)

    @Test
    void deveOrdenarFornecedoresRespondidosPorMenorValorTotalEDeixarAguardandoSemOrdem() {
        CotacaoCompra cotacao = cotacaoComStatus(StatusCotacaoCompra.ABERTA);
        CotacaoCompraFornecedor maisCaro = fornecedorConvite(cotacao, StatusCotacaoCompraFornecedor.RESPONDIDA);
        CotacaoCompraFornecedor maisBarato = fornecedorConvite(cotacao, StatusCotacaoCompraFornecedor.RESPONDIDA);
        CotacaoCompraFornecedor aguardando = fornecedorConvite(cotacao, StatusCotacaoCompraFornecedor.AGUARDANDO);
        when(cotacaoCompraFornecedorRepository.findAllByCotacaoId(cotacao.getId()))
                .thenReturn(List.of(maisCaro, maisBarato, aguardando));
        when(cotacaoCompraFornecedorItemRepository.findAllByCotacaoFornecedorId(maisCaro.getId()))
                .thenReturn(List.of(itemComPreco(maisCaro, new BigDecimal("100.00"))));
        when(cotacaoCompraFornecedorItemRepository.findAllByCotacaoFornecedorId(maisBarato.getId()))
                .thenReturn(List.of(itemComPreco(maisBarato, new BigDecimal("50.00"))));
        when(cotacaoCompraFornecedorItemRepository.findAllByCotacaoFornecedorId(aguardando.getId())).thenReturn(List.of());

        List<CotacaoCompraFornecedorResponseDTO> resultado = cotacaoCompraService.listarFornecedores(cotacao.getId(), TENANT_ID, USER_ID);

        assertThat(resultado).extracting(CotacaoCompraFornecedorResponseDTO::id, CotacaoCompraFornecedorResponseDTO::ordemSugerida)
                .containsExactlyInAnyOrder(
                        tuple(maisBarato.getId(), 1),
                        tuple(maisCaro.getId(), 2),
                        tuple(aguardando.getId(), null));
    }

    private CotacaoCompraFornecedorItem itemComPreco(CotacaoCompraFornecedor fornecedor, BigDecimal precoUnitario) {
        CotacaoCompraItem item = CotacaoCompraItem.builder().id(UUID.randomUUID()).produtoId(UUID.randomUUID()).quantidade(BigDecimal.ONE).build();
        return CotacaoCompraFornecedorItem.builder().cotacaoFornecedor(fornecedor).cotacaoItem(item).precoUnitario(precoUnitario).build();
    }
}
