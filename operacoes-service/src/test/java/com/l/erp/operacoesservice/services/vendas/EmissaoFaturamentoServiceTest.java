package com.l.erp.operacoesservice.services.vendas;

import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.domain.vendas.Pedido;
import com.l.erp.operacoesservice.domain.vendas.PedidoItem;
import com.l.erp.operacoesservice.domain.vendas.PedidoItemFiscalSnapshot;
import com.l.erp.operacoesservice.domain.vendas.enumerators.StatusEmissaoPedido;
import com.l.erp.operacoesservice.domain.vendas.enumerators.TipoItemPedido;
import com.l.erp.operacoesservice.infra.client.CadastroServiceClient;
import com.l.erp.operacoesservice.infra.client.EmissaoFiscalClient;
import com.l.erp.operacoesservice.repository.vendas.PedidoItemFiscalSnapshotRepository;
import com.l.erp.operacoesservice.repository.vendas.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmissaoFaturamentoServiceTest {

    private static final Long TENANT = 7L;

    @Mock private PedidoRepository pedidoRepository;
    @Mock private PedidoItemFiscalSnapshotRepository snapshotRepository;
    @Mock private CadastroServiceClient cadastroServiceClient;
    @Mock private EmissaoFiscalClient emissaoFiscalClient;

    private final List<PedidoItemFiscalSnapshot> snapshots = new ArrayList<>();

    private EmissaoFaturamentoService service;
    private Pedido pedido;
    private PedidoItem mercadoria;

    @BeforeEach
    void setUp() {
        service = spy(new EmissaoFaturamentoService(pedidoRepository, snapshotRepository, cadastroServiceClient,
                emissaoFiscalClient));
        pedido = new Pedido();
        pedido.setId(UUID.randomUUID());
        pedido.setTenantId(TENANT);
        pedido.setLastUpdatedBy(UUID.randomUUID());
        mercadoria = new PedidoItem();
        mercadoria.setTipoItem(TipoItemPedido.MERCADORIA);
    }

    private PedidoFaturadoEvent evento(PedidoItem... itens) {
        return new PedidoFaturadoEvent(pedido, List.of(itens), List.of());
    }

    @Test
    void enviaODocumentoEGuardaOIdQuandoAEmissaoAceita() {
        UUID documentoId = UUID.randomUUID();
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT)).thenReturn(Optional.of(pedido));
        doReturn(null).when(service).montarRequest(any(), any());
        when(emissaoFiscalClient.criarDocumento(any(), eq(pedido.getId().toString()), eq(TENANT), any()))
                .thenReturn(documentoId);

        service.aoFaturar(evento(mercadoria));

        assertThat(pedido.getDocumentoFiscalId()).isEqualTo(documentoId);
        assertThat(pedido.getStatusEmissao()).isEqualTo(StatusEmissaoPedido.ENVIADO);
        verify(pedidoRepository).save(pedido);
    }

    @Test
    void falhaNoEnvioViraFalhaEnvioSemPropagarExcecao() {
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT)).thenReturn(Optional.of(pedido));
        doReturn(null).when(service).montarRequest(any(), any());
        when(emissaoFiscalClient.criarDocumento(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("emissão fora do ar"));

        service.aoFaturar(evento(mercadoria));

        assertThat(pedido.getDocumentoFiscalId()).isNull();
        assertThat(pedido.getStatusEmissao()).isEqualTo(StatusEmissaoPedido.FALHA_ENVIO);
        assertThat(pedido.getMensagemEmissao()).isEqualTo("emissão fora do ar");
        verify(pedidoRepository).save(pedido);
    }

    @Test
    void pedidoSemItemFaturavelNaoDisparaEmissao() {
        service.aoFaturar(evento());

        verify(emissaoFiscalClient, never()).criarDocumento(any(), any(), any(), any());
        verify(emissaoFiscalClient, never()).criarNfse(any(), any(), any(), any());
        verify(pedidoRepository, never()).save(any());
    }

    // ---- NFS-e (fatia 3.7) ----

    @Test
    void pedidoSoDeServicoEnviaANfseEGuardaOIdNasColunasProprias() {
        UUID nfseId = UUID.randomUUID();
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT)).thenReturn(Optional.of(pedido));
        doReturn(null).when(service).montarNfseRequest(any(), any());
        when(emissaoFiscalClient.criarNfse(any(), eq(pedido.getId() + "-nfse"), eq(TENANT), any())).thenReturn(nfseId);

        service.aoFaturar(evento(servicoItem(new BigDecimal("100.00"))));

        assertThat(pedido.getDocumentoFiscalNfseId()).isEqualTo(nfseId);
        assertThat(pedido.getStatusEmissaoNfse()).isEqualTo(StatusEmissaoPedido.ENVIADO);
        assertThat(pedido.getDocumentoFiscalId()).isNull();
        assertThat(pedido.getStatusEmissao()).isNull();
        verify(emissaoFiscalClient, never()).criarDocumento(any(), any(), any(), any());
        verify(pedidoRepository).save(pedido);
    }

    @Test
    void pedidoMistoEmiteNfeENfseSemUmaSobrescreverAOutra() {
        UUID nfeId = UUID.randomUUID();
        UUID nfseId = UUID.randomUUID();
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT)).thenReturn(Optional.of(pedido));
        doReturn(null).when(service).montarRequest(any(), any());
        doReturn(null).when(service).montarNfseRequest(any(), any());
        when(emissaoFiscalClient.criarDocumento(any(), any(), any(), any())).thenReturn(nfeId);
        when(emissaoFiscalClient.criarNfse(any(), any(), any(), any())).thenReturn(nfseId);

        service.aoFaturar(evento(mercadoria, servicoItem(new BigDecimal("100.00"))));

        assertThat(pedido.getDocumentoFiscalId()).isEqualTo(nfeId);
        assertThat(pedido.getDocumentoFiscalNfseId()).isEqualTo(nfseId);
        verify(pedidoRepository).save(pedido);
    }

    @Test
    void falhaNaNfseViraFalhaEnvioSemDerrubarANfe() {
        UUID nfeId = UUID.randomUUID();
        when(pedidoRepository.findByIdAndTenantId(pedido.getId(), TENANT)).thenReturn(Optional.of(pedido));
        doReturn(null).when(service).montarRequest(any(), any());
        doReturn(null).when(service).montarNfseRequest(any(), any());
        when(emissaoFiscalClient.criarDocumento(any(), any(), any(), any())).thenReturn(nfeId);
        when(emissaoFiscalClient.criarNfse(any(), any(), any(), any())).thenThrow(new IllegalStateException("nfse fora do ar"));

        service.aoFaturar(evento(mercadoria, servicoItem(new BigDecimal("100.00"))));

        assertThat(pedido.getStatusEmissao()).isEqualTo(StatusEmissaoPedido.ENVIADO);
        assertThat(pedido.getStatusEmissaoNfse()).isEqualTo(StatusEmissaoPedido.FALHA_ENVIO);
        assertThat(pedido.getMensagemEmissaoNfse()).isEqualTo("nfse fora do ar");
        assertThat(pedido.getDocumentoFiscalNfseId()).isNull();
    }

    @Test
    void montaANfseDeUmEmitenteDoSimplesSemAliquotaESomandoOsItens() {
        prepararMontagem("1");
        PedidoItem a = servicoItem(new BigDecimal("100.00"));
        PedidoItem b = servicoItem(new BigDecimal("50.50"));
        prepararItem(a, "010101", "Consultoria", null, null);
        prepararItem(b, "010101", "Suporte", null, null);

        var req = service.montarNfseRequest(pedido, List.of(a, b));

        assertThat(req.valores().valorServico()).isEqualByComparingTo("150.50");
        assertThat(req.valores().aliquotaIssqn()).isNull();
        assertThat(req.valores().tipoRetencaoIssqn()).isEqualTo("1");
        assertThat(req.valores().tributacaoIssqn()).isEqualTo("1");
        assertThat(req.prestador().opcaoSimplesNacional()).isEqualTo("3");
        assertThat(req.prestador().regimeApuracaoSimples()).isEqualTo("1");
        assertThat(req.servico().codigoTributacaoNacional()).isEqualTo("010101");
        assertThat(req.servico().descricao()).isEqualTo("Consultoria; Suporte");
        assertThat(req.servico().municipioPrestacao()).isEqualTo("3104007");
        assertThat(req.tomador().documento()).isEqualTo("11222333000181");
        assertThat(req.ambiente()).isEqualTo("HOMOLOGACAO");
        assertThat(req.serie()).isEqualTo("1");
    }

    @Test
    void foraDoSimplesUsaAAliquotaEfetivaEMarcaRetencaoQuandoHa() {
        prepararMontagem("3");
        PedidoItem item = servicoItem(new BigDecimal("200.00"));
        prepararItem(item, "010101", "Consultoria", new BigDecimal("10.00"), new BigDecimal("10.00"));

        var req = service.montarNfseRequest(pedido, List.of(item));

        assertThat(req.prestador().opcaoSimplesNacional()).isEqualTo("1");
        assertThat(req.prestador().regimeApuracaoSimples()).isNull();
        assertThat(req.valores().aliquotaIssqn()).isEqualByComparingTo("5.00");
        assertThat(req.valores().tipoRetencaoIssqn()).isEqualTo("2");
    }

    @Test
    void servicosDeCodigosDiferentesNaoViramUmaNotaSo() {
        prepararMontagem("1");
        PedidoItem a = servicoItem(new BigDecimal("100.00"));
        PedidoItem b = servicoItem(new BigDecimal("100.00"));
        prepararItem(a, "010101", "Consultoria", null, null);
        prepararItem(b, "080101", "Treinamento", null, null);

        assertThatThrownBy(() -> service.montarNfseRequest(pedido, List.of(a, b)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(Constants.EMISSAO_NFSE_ERRO_SERVICOS_DISTINTOS);
    }

    @Test
    void servicoSemCodigoDeTributacaoNacionalFalhaComONomeDoServico() {
        prepararMontagem("1");
        PedidoItem item = servicoItem(new BigDecimal("100.00"));
        prepararItem(item, null, "Consultoria", null, null);

        assertThatThrownBy(() -> service.montarNfseRequest(pedido, List.of(item)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(Constants.EMISSAO_NFSE_ERRO_SEM_CODIGO_TRIBUTACAO.formatted("Consultoria"));
    }

    @Test
    void regimeTributarioNaoSuportadoFalhaAntesDeConsultarOCliente() {
        prepararMontagem("2");

        assertThatThrownBy(() -> service.montarNfseRequest(pedido, List.of(servicoItem(new BigDecimal("100.00")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(Constants.EMISSAO_NFSE_ERRO_CRT_NAO_SUPORTADO.formatted("2"));
        verify(cadastroServiceClient, never()).buscarClientePessoaId(any(), any(), any());
    }

    // ---- apoio ----

    private static PedidoItem servicoItem(BigDecimal valorTotal) {
        PedidoItem item = new PedidoItem();
        item.setId(UUID.randomUUID());
        item.setProdutoId(UUID.randomUUID());
        item.setTipoItem(TipoItemPedido.SERVICO);
        item.setValorTotal(valorTotal);
        return item;
    }

    /** Emitente (com o CRT dado), cliente/tomador, snapshots e as configurações {@code @Value} do serviço. */
    private void prepararMontagem(String crt) {
        ReflectionTestUtils.setField(service, "ambiente", "HOMOLOGACAO");
        ReflectionTestUtils.setField(service, "serie", "1");
        pedido.setClienteId(UUID.randomUUID());
        var endereco = new CadastroServiceClient.EnderecoEmissaoRef("Rua das Flores", "100", null, "Centro",
                "3104007", "Arcos", "MG", "35588000");
        when(cadastroServiceClient.buscarEmitenteEmissao(any(), any())).thenReturn(new CadastroServiceClient.EmitenteEmissaoRef(
                UUID.randomUUID(), "40152424000196", "ARCOS TECNOLOGIA LTDA", null, null, crt, endereco));
        lenient().when(cadastroServiceClient.buscarClientePessoaId(any(), any(), any())).thenReturn(UUID.randomUUID());
        lenient().when(cadastroServiceClient.buscarDestinatarioEmissao(any(), any(), any())).thenReturn(
                new CadastroServiceClient.DestinatarioEmissaoRef("11222333000181", "CLIENTE EXEMPLO LTDA", "1", null, endereco));
        lenient().when(snapshotRepository.findAllByPedidoItemInAndVersao(any(), eq(1))).thenAnswer(inv -> {
            List<PedidoItem> pedidos = inv.getArgument(0);
            return snapshots.stream().filter(s -> pedidos.contains(s.getPedidoItem())).toList();
        });
    }

    /** Snapshot v1 do item (com o ISS dado) e o produto com o cTribNac dado. */
    private void prepararItem(PedidoItem item, String codigoTributacao, String nome, BigDecimal valorIss,
                              BigDecimal valorIssRetido) {
        PedidoItemFiscalSnapshot snapshot = new PedidoItemFiscalSnapshot();
        snapshot.setPedidoItem(item);
        snapshot.setValorIss(valorIss);
        snapshot.setValorIssRetido(valorIssRetido);
        snapshots.add(snapshot);
        lenient().when(cadastroServiceClient.buscarProdutoParaNota(eq(item.getProdutoId()), any(), any())).thenReturn(
                new CadastroServiceClient.ProdutoNotaRef("SKU", nome, "UN", null, null, codigoTributacao));
    }
}
