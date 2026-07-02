package com.l.erp.billingservice.infra.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.billingservice.services.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;

@Component
public class AssinaturaRequestConsumer {

    private static final Logger log = LoggerFactory.getLogger(AssinaturaRequestConsumer.class);

    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    public AssinaturaRequestConsumer(SubscriptionService subscriptionService, ObjectMapper objectMapper) {
        this.subscriptionService = subscriptionService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "partner.assinatura.request", groupId = "billing-assinatura-group")
    @SendTo
    public String handleAssinaturaRequest(String tenantId) throws JsonProcessingException {
        log.info("Recebendo solicitação de resumo de assinatura via Kafka para tenantId={}", tenantId);
        var resumo = subscriptionService.getResumoAssinatura(Long.parseLong(tenantId));
        return objectMapper.writeValueAsString(resumo);
    }
}
