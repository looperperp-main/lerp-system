package com.l.erp.billingservice.services.recovery;

import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.domain.SubscriptionStatus;
import com.l.erp.billingservice.infra.asaas.AsaasGateway;
import com.l.erp.billingservice.infra.asaas.dto.AsaasPaymentData;
import com.l.erp.billingservice.infra.asaas.dto.AsaasPaymentResponse;
import com.l.erp.billingservice.infra.asaas.dto.AsaasWebhookPayload;
import com.l.erp.billingservice.infra.redis.DistributedLockService;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import com.l.erp.billingservice.services.webhook.handler.PaymentReceivedHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Reconciliação (Fase 7 — spec §27.x): pagamentos confirmados no Asaas cujo webhook não chegou.
 * Varre as assinaturas {@code AGUARDANDO_PAGAMENTO}, consulta a 1ª cobrança no Asaas e, se estiver
 * paga ({@code RECEIVED}/{@code CONFIRMED}), reusa o {@link PaymentReceivedHandler} para ativar.
 *
 * <p>Idempotente: ao ativar, a assinatura sai de {@code AGUARDANDO_PAGAMENTO}, então a próxima
 * execução não a reprocessa; a comissão é guardada por {@code asaas_payment_id} no engine.</p>
 */
@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);
    private static final Set<String> PAID = Set.of("RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH");

    private final DistributedLockService lockService;
    private final SubscriptionRepository subscriptionRepository;
    private final AsaasGateway asaasGateway;
    private final PaymentReceivedHandler paymentReceivedHandler;

    public ReconciliationJob(DistributedLockService lockService,
                             SubscriptionRepository subscriptionRepository,
                             AsaasGateway asaasGateway,
                             PaymentReceivedHandler paymentReceivedHandler) {
        this.lockService = lockService;
        this.subscriptionRepository = subscriptionRepository;
        this.asaasGateway = asaasGateway;
        this.paymentReceivedHandler = paymentReceivedHandler;
    }

    @Scheduled(cron = "${billing.cron.reconciliation}")
    public void run() {
        String lockKey = "syax:billing:lock:reconciliation";
        String lockOwner = UUID.randomUUID().toString();
        if (!lockService.acquire(lockKey, lockOwner, 600)) {
            log.info("Lock de reconciliation já adquirido — pulando");
            return;
        }
        try {
            List<Subscription> pendentes = subscriptionRepository.findByStatus(SubscriptionStatus.AGUARDANDO_PAGAMENTO);
            for (Subscription sub : pendentes) {
                try {
                    reconcile(sub);
                } catch (Exception e) {
                    log.error("Falha na reconciliação — subscription Asaas {}", sub.getAsaasSubscriptionId(), e);
                }
            }
        } finally {
            lockService.release(lockKey, lockOwner);
        }
    }

    private void reconcile(Subscription sub) {
        if (sub.getAsaasSubscriptionId() == null) {
            return;
        }
        AsaasPaymentResponse payment = asaasGateway.getFirstPayment(sub.getAsaasSubscriptionId());
        if (payment == null || !PAID.contains(payment.status())) {
            return;
        }

        log.warn("Reconciliação: pagamento {} ({}) confirmado no Asaas sem webhook — ativando tenant {}",
                payment.id(), payment.status(), sub.getTenantId());

        AsaasPaymentData pay = new AsaasPaymentData();
        pay.setId(payment.id());
        pay.setSubscription(sub.getAsaasSubscriptionId());
        pay.setStatus(payment.status());
        pay.setValue(payment.value());

        AsaasWebhookPayload payload = new AsaasWebhookPayload();
        payload.setEvent("PAYMENT_RECEIVED");
        payload.setPayment(pay);

        paymentReceivedHandler.handle(payload);
    }
}
