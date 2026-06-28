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

import java.util.List;

/**
 * Handler de {@code TRANSFER_FAILED} (Fase 6 — spec §8.10). Reverte as comissões do transfer para
 * {@code PENDENTE} e limpa o {@code asaas_transfer_id} — assim o próximo ciclo D+1 reprocessa.
 * O {@code failReason} (ex.: INVALID_PIX_KEY) é logado como alerta ao admin (sem coluna dedicada por ora).
 */
@Component
public class TransferFailedHandler implements WebhookEventHandler {

    private static final Logger log = LoggerFactory.getLogger(TransferFailedHandler.class);

    private final CommissionRepository commissionRepository;

    public TransferFailedHandler(CommissionRepository commissionRepository) {
        this.commissionRepository = commissionRepository;
    }

    @Override
    public String getEventType() {
        return "TRANSFER_FAILED";
    }

    @Override
    @Transactional
    public void handle(AsaasWebhookPayload payload) {
        if (payload.getTransfer() == null || payload.getTransfer().getId() == null) {
            throw new IllegalStateException("TRANSFER_FAILED sem transfer.id no payload");
        }
        String transferId = payload.getTransfer().getId();
        String failReason = payload.getTransfer().getFailReason();

        List<Commission> commissions = commissionRepository.findByAsaasTransferId(transferId);
        if (commissions.isEmpty()) {
            log.warn("TRANSFER_FAILED sem comissão correspondente — transferId={}", transferId);
            return;
        }

        commissions.forEach(c -> {
            c.setStatus(CommissionStatus.PENDENTE);
            c.setAsaasTransferId(null); // libera para nova tentativa no próximo ciclo
        });
        commissionRepository.saveAll(commissions);
        log.error("Transfer FALHOU — comissões revertidas para PENDENTE — transferId={} reason={} comissões={} partnerId={}",
                transferId, failReason, commissions.size(), commissions.getFirst().getPartnerId());
    }
}
