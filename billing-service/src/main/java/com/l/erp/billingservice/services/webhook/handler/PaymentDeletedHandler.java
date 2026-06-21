package com.l.erp.billingservice.services.webhook.handler;

import com.l.erp.billingservice.repository.CommissionRepository;
import com.l.erp.billingservice.infra.asaas.dto.AsaasWebhookPayload;
import com.l.erp.billingservice.services.webhook.WebhookEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler de {@code PAYMENT_DELETED} (spec §8.6).
 * Cancela a comissão gerada pelo pagamento deletado, se houver.
 */
@Component
public class PaymentDeletedHandler implements WebhookEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentDeletedHandler.class);

    private static final String COMMISSION_CANCELADO = "CANCELADO";

    private final CommissionRepository commissionRepository;

    public PaymentDeletedHandler(CommissionRepository commissionRepository) {
        this.commissionRepository = commissionRepository;
    }

    @Override
    public String getEventType() {
        return "PAYMENT_DELETED";
    }

    @Override
    @Transactional
    public void handle(AsaasWebhookPayload payload) {
        if (payload.getPayment() == null || payload.getPayment().getId() == null) {
            throw new IllegalStateException("PAYMENT_DELETED sem payment.id no payload");
        }
        String asaasPaymentId = payload.getPayment().getId();

        commissionRepository.findByAsaasPaymentId(asaasPaymentId).ifPresent(commission -> {
            commission.setStatus(COMMISSION_CANCELADO);
            commissionRepository.save(commission);
            log.info("Comissão cancelada por PAYMENT_DELETED: asaasPaymentId={}", asaasPaymentId);
        });
    }
}