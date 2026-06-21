package com.l.erp.billingservice.services.webhook.handler;

import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.domain.SubscriptionStatus;
import com.l.erp.billingservice.infra.asaas.dto.AsaasWebhookPayload;
import com.l.erp.billingservice.infra.config.DunningProperties;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import com.l.erp.billingservice.services.webhook.WebhookEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Handler de {@code PAYMENT_OVERDUE} (spec §27.7.3).
 *
 * <p>Grava os timestamps absolutos de dunning ({@code suspend_at}, {@code cancel_at}) — o
 * relógio é da Syax. O {@code DunningJob} (fase posterior) compara esses timestamps com
 * {@code now()} e executa lembrete/suspensão/cancelamento. Este handler não muda o status.</p>
 */
@Component
public class PaymentOverdueHandler implements WebhookEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentOverdueHandler.class);

    private final SubscriptionRepository subscriptionRepository;
    private final DunningProperties dunningProps;

    public PaymentOverdueHandler(SubscriptionRepository subscriptionRepository,
                                 DunningProperties dunningProps) {
        this.subscriptionRepository = subscriptionRepository;
        this.dunningProps = dunningProps;
    }

    @Override
    public String getEventType() {
        return "PAYMENT_OVERDUE";
    }

    @Override
    @Transactional
    public void handle(AsaasWebhookPayload payload) {
        if (payload.getPayment() == null || payload.getPayment().getSubscription() == null) {
            throw new IllegalStateException("PAYMENT_OVERDUE sem subscription no payload");
        }
        String asaasSubscriptionId = payload.getPayment().getSubscription();

        Subscription sub = subscriptionRepository.findByAsaasSubscriptionId(asaasSubscriptionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Subscription Asaas não encontrada: " + asaasSubscriptionId));

        // Guard de estado (§28.6): dunning só inicia a partir de ATIVA
        if (!SubscriptionStatus.ATIVA.equals(sub.getStatus())) {
            log.info("PAYMENT_OVERDUE ignorado — tenant {} em estado {}",
                    sub.getTenantId(), sub.getStatus());
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        sub.setSuspendAt(now.plusDays(dunningProps.getGracePeriodDays()));
        sub.setCancelAt(now.plusDays(dunningProps.getCancelAfterDays()));
        sub.setGracePeriodExpiresAt(sub.getSuspendAt()); // compatibilidade
        sub.setReminderSentAt(null);
        sub.setUpdatedAt(now);
        subscriptionRepository.save(sub);

        // Email 1 (aviso imediato) será disparado quando o NotificationService entrar na fase de dunning.
        log.warn("Dunning iniciado — tenant {} suspendAt={} cancelAt={}",
                sub.getTenantId(), sub.getSuspendAt(), sub.getCancelAt());
    }
}