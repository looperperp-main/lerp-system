package com.l.erp.billingservice.services.webhook.handler;

import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.domain.SubscriptionStatus;
import com.l.erp.billingservice.infra.asaas.AsaasException;
import com.l.erp.billingservice.infra.asaas.AsaasGateway;
import com.l.erp.billingservice.infra.asaas.dto.AsaasPaymentData;
import com.l.erp.billingservice.infra.asaas.dto.AsaasWebhookPayload;
import com.l.erp.billingservice.infra.exception.TransientException;
import com.l.erp.billingservice.infra.kafka.KafkaBillingProducerService;
import com.l.erp.billingservice.infra.redis.TenantStatusCacheService;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import com.l.erp.billingservice.services.webhook.WebhookEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Handler de {@code PAYMENT_RECEIVED} / {@code PAYMENT_CONFIRMED} (spec §8.3, §28.3, §28.8).
 *
 * <p>Ativa a assinatura, zera o dunning, faz write-through no cache e publica
 * {@code billing.subscription.activated} (consumido pelo partner-service para comissão e
 * pelo auth-service para ativar o tenant). Roda em TODO pagamento confirmado — ativação e
 * renovações — porque a comissão recorrente é por pagamento.</p>
 */
@Component
public class PaymentReceivedHandler implements WebhookEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReceivedHandler.class);

    private final SubscriptionRepository subscriptionRepository;
    private final KafkaBillingProducerService kafkaProducer;
    private final TenantStatusCacheService tenantStatusCache;
    private final AsaasGateway asaasGateway;

    public PaymentReceivedHandler(SubscriptionRepository subscriptionRepository,
                                  KafkaBillingProducerService kafkaProducer,
                                  TenantStatusCacheService tenantStatusCache,
                                  AsaasGateway asaasGateway) {
        this.subscriptionRepository = subscriptionRepository;
        this.kafkaProducer = kafkaProducer;
        this.tenantStatusCache = tenantStatusCache;
        this.asaasGateway = asaasGateway;
    }

    @Override
    public String getEventType() {
        return "PAYMENT_RECEIVED";
    }

    @Override
    @Transactional
    public void handle(AsaasWebhookPayload payload) {
        AsaasPaymentData payment = payload.getPayment();
        if (payment == null || payment.getSubscription() == null) {
            throw new IllegalStateException("PAYMENT_RECEIVED sem subscription no payload");
        }

        String asaasSubscriptionId = payment.getSubscription();
        String asaasPaymentId = payment.getId();
        BigDecimal receivedValue = payment.getValue();

        // nextDueDate é a fonte da verdade do Asaas (§8.3, §28.3) — NUNCA calcular com plusDays(30/365).
        // Buscado ANTES de tocar o banco: a conexão JPA é adquirida lazy na 1ª query (findBy abaixo),
        // então o round-trip HTTP não segura conexão do pool. Falha → TransientException (Asaas retenta).
        LocalDate nextDueDate;
        try {
            nextDueDate = asaasGateway.getSubscription(asaasSubscriptionId).nextDueDate();
        } catch (AsaasException e) {
            throw new TransientException(
                    "Falha ao consultar nextDueDate no Asaas para " + asaasSubscriptionId, e);
        }

        Subscription sub = subscriptionRepository.findByAsaasSubscriptionId(asaasSubscriptionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Subscription Asaas não encontrada: " + asaasSubscriptionId));

        // Guard de estado (§27.7.6): pagamento após cancelamento não reativa — admin decide
        if (SubscriptionStatus.CANCELADO.equals(sub.getStatus())) {
            log.warn("PAYMENT_RECEIVED para tenant {} CANCELADO — aguardando decisão admin. asaasPaymentId={}",
                    sub.getTenantId(), asaasPaymentId);
            return;
        }

        // Validação de valor (§28.8): divergência não bloqueia ativação, mas alerta
        if (receivedValue != null && sub.getValue() != null
                && receivedValue.compareTo(sub.getValue()) != 0) {
            log.warn("Valor divergente — esperado={} recebido={} tenant={}",
                    sub.getValue(), receivedValue, sub.getTenantId());
        }

        OffsetDateTime now = OffsetDateTime.now();
        boolean primeiraAtivacao = !SubscriptionStatus.ATIVA.equals(sub.getStatus());

        sub.setStatus(SubscriptionStatus.ATIVA);
        if (primeiraAtivacao) {
            sub.setActivatedAt(now);
        }
        if (nextDueDate != null) {
            sub.setNextDueDate(nextDueDate.atStartOfDay().atOffset(ZoneOffset.UTC));
        }
        // Zera o dunning (reativação) — §27.7.9.
        sub.setSuspendAt(null);
        sub.setCancelAt(null);
        sub.setReminderSentAt(null);
        sub.setGracePeriodExpiresAt(null);
        sub.setUpdatedAt(now);
        subscriptionRepository.save(sub);

        // Write-through no cache — o handler conhece o novo status (§28.3)
        tenantStatusCache.put(sub.getTenantId(), SubscriptionStatus.ATIVA);

        // Comissão é calculada sobre o valor recebido (§28.8)
        BigDecimal valueForEvent = receivedValue != null ? receivedValue : sub.getValue();
        kafkaProducer.sendSubscriptionActivated(
                sub.getTenantId(), sub.getPlanType(), valueForEvent,
                sub.getAsaasSubscriptionId(), asaasPaymentId, now);

        log.info("Tenant {} ativado via PAYMENT_RECEIVED (primeira={}) asaasPaymentId={}",
                sub.getTenantId(), primeiraAtivacao, asaasPaymentId);
    }
}