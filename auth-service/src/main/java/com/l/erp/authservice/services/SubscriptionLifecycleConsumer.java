package com.l.erp.authservice.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.authservice.dominio.enumerators.EnumTenantStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Fecha o gap da Fase 5: consome {@code billing.subscription.suspended}/{@code .cancelled}
 * (publicados pelo DunningJob do billing) e propaga para {@code auth.tenant.status}, de modo que
 * o login do tenant suspenso/cancelado passe a ser barrado localmente (sem chamada síncrona ao billing).
 */
@Service
public class SubscriptionLifecycleConsumer {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionLifecycleConsumer.class);

    private final ObjectMapper objectMapper;
    private final TenantService tenantService;

    public SubscriptionLifecycleConsumer(ObjectMapper objectMapper, TenantService tenantService) {
        this.objectMapper = objectMapper;
        this.tenantService = tenantService;
    }

    @KafkaListener(topics = "billing.subscription.suspended", groupId = "auth-service-group")
    public void consumeSuspended(String payload) {
        apply(payload, EnumTenantStatus.SUSPENSO, "billing.subscription.suspended");
    }

    @KafkaListener(topics = "billing.subscription.cancelled", groupId = "auth-service-group")
    public void consumeCancelled(String payload) {
        apply(payload, EnumTenantStatus.CANCELADO, "billing.subscription.cancelled");
    }

    private void apply(String payload, EnumTenantStatus status, String topic) {
        logger.info("Recebido evento {}", topic);
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});
            Long tenantId = data.get("tenantId") instanceof Number n ? n.longValue() : null;
            if (tenantId == null) {
                logger.warn("{} sem tenantId — ignorando. Payload: {}", topic, payload);
                return;
            }
            tenantService.applyBillingStatus(tenantId, status);
        } catch (Exception e) {
            logger.error("Falha ao processar {}. Payload: {}", topic, payload, e);
        }
    }
}
