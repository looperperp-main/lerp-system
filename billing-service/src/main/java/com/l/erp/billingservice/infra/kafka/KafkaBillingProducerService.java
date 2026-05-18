package com.l.erp.billingservice.infra.kafka;

import com.l.erp.common.api.dto.AuditEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class KafkaBillingProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaBillingProducerService.class);
        private static final String TOPIC = "billing.subscription.activated";
    private static final String AUDIT_TOPIC = "audit.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaBillingProducerService(KafkaTemplate<String, String> kafkaTemplate,
                                        ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendSubscriptionActivated(Long tenantId, String planType, BigDecimal value,
                                           String asaasSubscriptionId, String asaasPaymentId,
                                           OffsetDateTime activatedAt) {
        try {
            java.util.HashMap<String, Object> payload = new java.util.HashMap<>();
            payload.put("tenantId", tenantId);
            payload.put("planType", planType);
            payload.put("value", value);
            payload.put("asaasSubscriptionId", asaasSubscriptionId);
            payload.put("asaasPaymentId", asaasPaymentId != null ? asaasPaymentId : "");
            payload.put("activatedAt", activatedAt.toString());
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(TOPIC, String.valueOf(tenantId), json);
            log.info("billing.subscription.activated publicado tenantId={} planType={}", tenantId, planType);
        } catch (Exception e) {
            log.error("Falha ao publicar billing.subscription.activated tenantId={}", tenantId, e);
        }
    }

    public void sendAuditEvent(String action, UUID actorId, String targetType, UUID targetId, String result, String detailsJson, UUID correlationId) {
        try {
            AuditEventDTO event = new AuditEventDTO(action, actorId, targetType, targetId, result, detailsJson, correlationId, Instant.now());
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(AUDIT_TOPIC, targetId != null ? targetId.toString() : action, json);
            log.info("audit.events publicado action={} actor={}", action, actorId);
        } catch (Exception e) {
            log.error("Falha ao publicar audit.events action={}", action, e);
        }
    }
}