package com.l.erp.billingservice.services.webhook.handler;

import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.infra.asaas.dto.AsaasWebhookPayload;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import com.l.erp.billingservice.services.webhook.WebhookEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Handler de {@code SUBSCRIPTION_INACTIVATED} (spec §27.7.5).
 *
 * <p>Apenas metadado: grava {@code asaas_inactivated_at}. NÃO comanda transição de estado —
 * o {@code DunningJob} controla suspensão/cancelamento via {@code cancel_at}. O relógio é da
 * Syax, não do Asaas.</p>
 */
@Component
public class SubscriptionInactivatedHandler implements WebhookEventHandler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionInactivatedHandler.class);

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionInactivatedHandler(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    public String getEventType() {
        return "SUBSCRIPTION_INACTIVATED";
    }

    @Override
    @Transactional
    public void handle(AsaasWebhookPayload payload) {
        if (payload.getSubscription() == null || payload.getSubscription().getId() == null) {
            throw new IllegalStateException("SUBSCRIPTION_INACTIVATED sem subscription no payload");
        }
        String asaasSubscriptionId = payload.getSubscription().getId();

        Subscription sub = subscriptionRepository.findByAsaasSubscriptionId(asaasSubscriptionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Subscription Asaas não encontrada: " + asaasSubscriptionId));

        sub.setAsaasInactivatedAt(OffsetDateTime.now());
        subscriptionRepository.save(sub);

        log.info("Asaas inativou assinatura — DunningJob controla o cancelamento. tenantId={} status={}",
                sub.getTenantId(), sub.getStatus());
    }
}