package com.l.erp.billingservice.services;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.domain.WebhookLog;
import com.l.erp.billingservice.infra.kafka.KafkaBillingProducerService;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import com.l.erp.billingservice.repository.WebhookLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class WebhookProcessorService {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessorService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final WebhookLogRepository webhookLogRepository;
    private final KafkaBillingProducerService kafkaProducer;
    private final ObjectMapper objectMapper;

    public WebhookProcessorService(SubscriptionRepository subscriptionRepository,
                                    WebhookLogRepository webhookLogRepository,
                                    KafkaBillingProducerService kafkaProducer,
                                    ObjectMapper objectMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.webhookLogRepository = webhookLogRepository;
        this.kafkaProducer = kafkaProducer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void process(String rawPayload, WebhookLog webhookLog) {
        try {
            Map<String, Object> payload = objectMapper.readValue(rawPayload, new TypeReference<>() {});
            String event = (String) payload.get("event");

            if ("PAYMENT_RECEIVED".equals(event) || "PAYMENT_CONFIRMED".equals(event)) {
                processPaymentReceived(payload, webhookLog);
            } else {
                webhookLog.setStatus("IGNORADO");
                webhookLog.setProcessedAt(OffsetDateTime.now());
                webhookLogRepository.save(webhookLog);
                log.info("Webhook ignorado event={}", event);
            }
        } catch (Exception e) {
            webhookLog.setStatus("ERRO");
            webhookLog.setErrorMessage(e.getMessage());
            webhookLogRepository.save(webhookLog);
            log.error("Erro ao processar webhook id={}", webhookLog.getId(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void processPaymentReceived(Map<String, Object> payload, WebhookLog webhookLog) {
        // Extrai asaasSubscriptionId (pode estar em payload.subscription.id ou payload.payment.subscription)
        String asaasSubscriptionId = extractSubscriptionId(payload);
        String asaasPaymentId = extractPaymentId(payload);

        if (asaasSubscriptionId == null) {
            webhookLog.setStatus("IGNORADO");
            webhookLog.setProcessedAt(OffsetDateTime.now());
            webhookLogRepository.save(webhookLog);
            log.warn("PAYMENT_RECEIVED sem campo subscription — ignorado");
            return;
        }

        Optional<Subscription> opt = subscriptionRepository.findByAsaasSubscriptionId(asaasSubscriptionId);
        if (opt.isEmpty()) {
            webhookLog.setStatus("IGNORADO");
            webhookLog.setErrorMessage("Subscription não encontrada: " + asaasSubscriptionId);
            webhookLog.setProcessedAt(OffsetDateTime.now());
            webhookLogRepository.save(webhookLog);
            log.warn("Subscription Asaas {} não encontrada no banco", asaasSubscriptionId);
            return;
        }

        Subscription sub = opt.get();
        OffsetDateTime now = OffsetDateTime.now();
        boolean primeiroPageamento = !"ATIVA".equals(sub.getStatus());

        if (primeiroPageamento) {
            sub.setStatus("ATIVA");
            sub.setActivatedAt(now);
            subscriptionRepository.save(sub);
            log.info("Subscription {} ativada para tenantId={}", asaasSubscriptionId, sub.getTenantId());
        } else {
            log.info("Renovação recebida para subscription {} tenantId={}", asaasSubscriptionId, sub.getTenantId());
        }

        // Publica evento para TODOS os pagamentos (ativação + renovações) — comissão é calculada pelo partner-service
        kafkaProducer.sendSubscriptionActivated(
                sub.getTenantId(), sub.getPlanType(), sub.getValue(),
                sub.getAsaasSubscriptionId(), asaasPaymentId, now);

        webhookLog.setTenantId(sub.getTenantId());
        webhookLog.setAsaasSubscriptionId(asaasSubscriptionId);
        webhookLog.setStatus("PROCESSADO");
        webhookLog.setProcessedAt(now);
        webhookLogRepository.save(webhookLog);
    }

    @SuppressWarnings("unchecked")
    private String extractSubscriptionId(Map<String, Object> payload) {
        // Tenta payload.subscription.id (formato antigo)
        if (payload.get("subscription") instanceof Map<?, ?> m) {
            return (String) ((Map<String, Object>) m).get("id");
        }
        // Tenta payload.payment.subscription (string direta — formato Asaas atual)
        if (payload.get("payment") instanceof Map<?, ?> pm) {
            Object sub = ((Map<String, Object>) pm).get("subscription");
            return sub instanceof String s ? s : null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractPaymentId(Map<String, Object> payload) {
        if (payload.get("payment") instanceof Map<?, ?> pm) {
            return (String) ((Map<String, Object>) pm).get("id");
        }
        return (String) payload.get("id");
    }
}