package com.l.erp.authservice.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.authservice.services.audit.ConsumerErrorLogService;
import com.l.erp.common.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Consome {@code billing.subscription.activated} (publicado pelo billing-service ao confirmar
 * pagamento) e ativa a assinatura do tenant no auth-service.
 *
 * <p>Desacoplamento proposital: o auth-service é o dono do acesso do tenant e mantém o estado
 * localmente. O billing-service pode estar fora do ar sem impactar login — quando voltar, os
 * eventos pendentes no Kafka são reprocessados e o estado converge.</p>
 */
@Service
public class SubscriptionActivatedConsumer {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionActivatedConsumer.class);

    private final ObjectMapper objectMapper;
    private final TenantService tenantService;
    private final ConsumerErrorLogService consumerErrorLogService;

    public SubscriptionActivatedConsumer(ObjectMapper objectMapper, TenantService tenantService,
                                         ConsumerErrorLogService consumerErrorLogService) {
        this.objectMapper = objectMapper;
        this.tenantService = tenantService;
        this.consumerErrorLogService = consumerErrorLogService;
    }

    @KafkaListener(topics = Constants.TOPIC_SUBSCRIPTION_ACTIVATED, groupId = Constants.AUTH_SERVICE_GROUP)
    public void consume(String payload) {
        logger.info("Recebido evento billing.subscription.activated");
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});

            Long tenantId = data.get("tenantId") instanceof Number n ? n.longValue() : null;
            String planType = (String) data.get("planType");
            String asaasSubscriptionId = (String) data.get("asaasSubscriptionId");

            if (tenantId == null) {
                logger.warn("billing.subscription.activated sem tenantId — ignorando. Payload: {}", payload);
                return;
            }

            tenantService.activateSubscription(tenantId, planType, asaasSubscriptionId);
        } catch (Exception e) {
            // Falha ao ativar o tenant não pode se perder no commit do offset: persiste na DLQ
            // (audit.consumer_error_log) para reprocessamento manual/agendado.
            logger.error("Falha ao processar billing.subscription.activated — gravando na DLQ. Payload: {}", payload, e);
            consumerErrorLogService.record(Constants.AUTH_SERVICE_NAME, Constants.TOPIC_SUBSCRIPTION_ACTIVATED, payload, e);
        }
    }
}