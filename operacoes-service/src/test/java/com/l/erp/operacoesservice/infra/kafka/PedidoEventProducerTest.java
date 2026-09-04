package com.l.erp.operacoesservice.infra.kafka;

import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.domain.vendas.Pedido;
import com.l.erp.operacoesservice.domain.vendas.PedidoItem;
import com.l.erp.operacoesservice.domain.vendas.enumerators.TipoItemPedido;
import com.l.erp.operacoesservice.infra.client.CadastroServiceClient;
import com.l.erp.operacoesservice.services.vendas.PedidoCanceladoEvent;
import com.l.erp.operacoesservice.services.vendas.PedidoConfirmadoEvent;
import com.l.erp.operacoesservice.services.vendas.PedidoFaturadoEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CadastroServiceClient cadastroServiceClient;

    private PedidoEventProducer producer;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PRODUTO_ID = UUID.randomUUID();

    private Pedido pedido() {
        Pedido p = Pedido.builder()
                .id(UUID.randomUUID())
                .numero(100L)
                .clienteId(UUID.randomUUID())
                .valorTotal(BigDecimal.TEN)
                .dataConfirmacao(Instant.now())
                .build();
        p.setTenantId(TENANT_ID);
        p.setLastUpdatedBy(USER_ID);
        return p;
    }

    private PedidoItem item() {
        return PedidoItem.builder()
                .produtoId(PRODUTO_ID)
                .tipoItem(TipoItemPedido.MERCADORIA)
                .quantidade(BigDecimal.ONE)
                .valorTotal(BigDecimal.TEN)
                .build();
    }

    // P4 (spec/o2c-vendas.md §8): confirma que o item enriquecido carrega ncm/codigoServico do
    // Produto buscado no cadastro-service.
    @Test
    void publicarConfirmado_deveEnriquecerItensComNcmECodigoServico() {
        producer = new PedidoEventProducer(kafkaTemplate, objectMapper, cadastroServiceClient);
        Pedido p = pedido();
        when(cadastroServiceClient.buscarProduto(PRODUTO_ID, TENANT_ID, USER_ID))
                .thenReturn(new CadastroServiceClient.ProdutoRef("MERCADORIA", "000001", true, "8471.30.19", null));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        producer.publicarConfirmado(new PedidoConfirmadoEvent(p, List.of(item())));

        // 2 invocações: payload do evento (enviar) + AuditEventDTO (auditar) — a primeira é a que
        // interessa aqui.
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(objectMapper, Mockito.times(2)).writeValueAsString(payloadCaptor.capture());
        assertThat(payloadCaptor.getAllValues().get(0).toString())
                .contains("ncm=8471.30.19")
                .contains("codigoServico=000001");

        verify(kafkaTemplate).send(Constants.PEDIDO_CONFIRMADO_TOPIC, p.getId().toString(), "{}");
        verify(kafkaTemplate).send(Constants.AUDIT_TOPIC, p.getId().toString(), "{}");
    }

    // ponytail: fail-soft — se o cadastro-service falhar pra um produto, o item vai sem
    // ncm/codigoServico, mas o evento ainda é publicado (não derruba a publicação inteira).
    @Test
    void publicarConfirmado_quandoCadastroServiceFalha_itemVaiSemNcmMasEventoPublica() {
        producer = new PedidoEventProducer(kafkaTemplate, objectMapper, cadastroServiceClient);
        Pedido p = pedido();
        when(cadastroServiceClient.buscarProduto(PRODUTO_ID, TENANT_ID, USER_ID))
                .thenThrow(new RuntimeException("cadastro-service indisponível"));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        producer.publicarConfirmado(new PedidoConfirmadoEvent(p, List.of(item())));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(objectMapper, Mockito.times(2)).writeValueAsString(payloadCaptor.capture());
        assertThat(payloadCaptor.getAllValues().get(0).toString())
                .contains("ncm=null")
                .contains("codigoServico=null");

        verify(kafkaTemplate).send(Constants.PEDIDO_CONFIRMADO_TOPIC, p.getId().toString(), "{}");
    }

    @Test
    void publicarFaturado_deveEnviarParaTopicoDeFaturamento() {
        producer = new PedidoEventProducer(kafkaTemplate, objectMapper, cadastroServiceClient);
        Pedido p = pedido();
        p.setDataFaturamento(Instant.now());
        when(cadastroServiceClient.buscarProduto(any(UUID.class), any(Long.class), any(UUID.class))).thenReturn(null);
        when(cadastroServiceClient.buscarClientePessoaId(any(UUID.class), any(Long.class), any(UUID.class))).thenReturn(UUID.randomUUID());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        producer.publicarFaturado(new PedidoFaturadoEvent(p, List.of(item()), List.of()));

        verify(kafkaTemplate).send(Constants.PEDIDO_FATURADO_TOPIC, p.getId().toString(), "{}");
    }

    @Test
    void publicarCancelado_deveEnviarParaTopicoDeCancelamento() {
        producer = new PedidoEventProducer(kafkaTemplate, objectMapper, cadastroServiceClient);
        Pedido p = pedido();
        p.setDataCancelamento(Instant.now());
        when(cadastroServiceClient.buscarProduto(any(UUID.class), any(Long.class), any(UUID.class))).thenReturn(null);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        producer.publicarCancelado(new PedidoCanceladoEvent(p, List.of(item())));

        verify(kafkaTemplate).send(Constants.PEDIDO_CANCELADO_TOPIC, p.getId().toString(), "{}");
    }

    @Test
    void enviar_quandoSerializacaoFalha_apenasLoga() {
        producer = new PedidoEventProducer(kafkaTemplate, objectMapper, cadastroServiceClient);
        Pedido p = pedido();
        when(cadastroServiceClient.buscarProduto(any(UUID.class), any(Long.class), any(UUID.class))).thenReturn(null);
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("json boom"));

        producer.publicarConfirmado(new PedidoConfirmadoEvent(p, List.of(item())));

        verify(kafkaTemplate, Mockito.never()).send(anyString(), anyString(), anyString());
    }
}
