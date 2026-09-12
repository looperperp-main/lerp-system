package com.l.erp.operacoesservice.infra.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.l.erp.common.api.dto.AuditEventDTO;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.domain.compras.PedidoCompra;
import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoria;
import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoriaItem;
import com.l.erp.operacoesservice.services.compras.RecebimentoCanceladoEvent;
import com.l.erp.operacoesservice.services.compras.RecebimentoConfirmadoEvent;
import com.l.erp.operacoesservice.util.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Publica os eventos de confirmação/cancelamento de recebimento (spec/p2p-compras.md
 * §"Integração com estoque", Fase 3) + o AuditEventDTO correspondente — mesmo padrão do
 * PedidoEventProducer (vendas). Chamado só por RecebimentoEventListener (AFTER_COMMIT).
 *
 * ponytail: falha de publicação só loga (mesmo padrão do PedidoEventProducer) — não derruba a
 * transação HTTP, que já commitou (o estoque já foi baixado in-process, na mesma transação).
 */
@Service
public class RecebimentoEventProducer {

    private static final Logger log = LoggerFactory.getLogger(RecebimentoEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public RecebimentoEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publicarConfirmado(RecebimentoConfirmadoEvent event) {
        RecebimentoMercadoria r = event.recebimento();
        PedidoCompra pedido = event.pedido();
        List<ItemPayload> itens = event.itens().stream()
                .map(i -> new ItemPayload(i.getPedidoItem().getProdutoId(), pedido.getFornecedorId(),
                        i.getQuantidade(), i.getPrecoUnitarioNf()))
                .toList();
        PayloadConfirmado payload = new PayloadConfirmado(UUID.randomUUID(), r.getTenantId(), r.getId(),
                r.getDepositoId(), itens);
        enviar(Constants.RECEBIMENTO_CONFIRMADO_TOPIC, r, payload);
        auditar(Constants.AUDIT_ACAO_RECEBIMENTO_CONFIRMADO, r);
    }

    public void publicarCancelado(RecebimentoCanceladoEvent event) {
        RecebimentoMercadoria r = event.recebimento();
        PayloadCancelado payload = new PayloadCancelado(UUID.randomUUID(), r.getTenantId(), r.getId());
        enviar(Constants.RECEBIMENTO_CANCELADO_TOPIC, r, payload);
        auditar(Constants.AUDIT_ACAO_RECEBIMENTO_CANCELADO, r);
    }

    private void enviar(String topic, RecebimentoMercadoria recebimento, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, recebimento.getId().toString(), json);
            log.info("{} publicado recebimentoId={} tenantId={}", topic, recebimento.getId(), recebimento.getTenantId());
        } catch (Exception e) {
            log.error("Falha ao publicar {} recebimentoId={}", topic, recebimento.getId(), e);
        }
    }

    private void auditar(String acao, RecebimentoMercadoria recebimento) {
        try {
            UUID correlationId = SecurityUtils.getCorrelationIdFromRequest(log);
            AuditEventDTO evento = new AuditEventDTO(acao, recebimento.getLastUpdatedBy(), Constants.RECEBIMENTO_COMPRA,
                    recebimento.getId(), Constants.SUCCESS, null, correlationId, Instant.now());
            kafkaTemplate.send(Constants.AUDIT_TOPIC, recebimento.getId().toString(), objectMapper.writeValueAsString(evento));
        } catch (Exception e) {
            log.error("Falha ao publicar audit.events acao={} recebimentoId={}", acao, recebimento.getId(), e);
        }
    }

    // Payload rico do spec (§"Integração com estoque"): produto_id, fornecedor_id, quantidade,
    // preco_unitario_nf — fornecedorId vem do pedido (mesmo fornecedor pra todos os itens).
    private record ItemPayload(@JsonProperty("produto_id") UUID produtoId,
                                @JsonProperty("fornecedor_id") UUID fornecedorId,
                                @JsonProperty("quantidade") BigDecimal quantidade,
                                @JsonProperty("preco_unitario_nf") BigDecimal precoUnitarioNf) {
    }

    private record PayloadConfirmado(@JsonProperty("event_id") UUID eventId,
                                      @JsonProperty("tenant_id") Long tenantId,
                                      @JsonProperty("recebimento_id") UUID recebimentoId,
                                      @JsonProperty("deposito_id") UUID depositoId,
                                      @JsonProperty("itens") List<ItemPayload> itens) {
    }

    private record PayloadCancelado(@JsonProperty("event_id") UUID eventId,
                                     @JsonProperty("tenant_id") Long tenantId,
                                     @JsonProperty("recebimento_id") UUID recebimentoId) {
    }
}
