package com.l.erp.operacoesservice.services.vendas;

import com.l.erp.operacoesservice.domain.vendas.Pedido;
import com.l.erp.operacoesservice.domain.vendas.PedidoItem;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
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
    void pedidoSoDeServicoNaoDisparaEmissao() {
        PedidoItem servico = new PedidoItem();
        servico.setTipoItem(TipoItemPedido.SERVICO);

        service.aoFaturar(evento(servico));

        verify(emissaoFiscalClient, never()).criarDocumento(any(), any(), any(), any());
        verify(pedidoRepository, never()).save(any());
    }
}
