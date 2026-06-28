package com.l.erp.billingservice.services.webhook.handler;

import com.l.erp.billingservice.domain.Commission;
import com.l.erp.billingservice.domain.CommissionStatus;
import com.l.erp.billingservice.infra.asaas.dto.AsaasWebhookPayload;
import com.l.erp.billingservice.repository.CommissionRepository;
import com.l.erp.billingservice.services.webhook.WebhookEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Handler de {@code TRANSFER_COMPLETED} (Fase 6 — spec §8.9). Confirma o repasse: as comissões do
 * transfer ({@code EM_TRANSFERENCIA}) viram {@code PAGO}. Um transfer cobre N comissões (lote por parceiro/período).
 */
@Component
public class TransferCompletedHandler implements WebhookEventHandler {

    private static final Logger log = LoggerFactory.getLogger(TransferCompletedHandler.class);

    private final CommissionRepository commissionRepository;

    public TransferCompletedHandler(CommissionRepository commissionRepository) {
        this.commissionRepository = commissionRepository;
    }

    @Override
    public String getEventType() {
        return "TRANSFER_COMPLETED";
    }

    @Override
    @Transactional
    public void handle(AsaasWebhookPayload payload) {
        if (payload.getTransfer() == null || payload.getTransfer().getId() == null) {
            throw new IllegalStateException("TRANSFER_COMPLETED sem transfer.id no payload");
        }
        String transferId = payload.getTransfer().getId();

        List<Commission> commissions = commissionRepository.findByAsaasTransferId(transferId);
        if (commissions.isEmpty()) {
            log.warn("TRANSFER_COMPLETED sem comissão correspondente — transferId={}", transferId);
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        commissions.forEach(c -> {
            c.setStatus(CommissionStatus.PAGO);
            c.setPaidAt(now);
        });
        commissionRepository.saveAll(commissions);
        log.info("Repasse confirmado via TRANSFER_COMPLETED — transferId={} comissões={} partnerId={}",
                transferId, commissions.size(), commissions.getFirst().getPartnerId());
    }
}
