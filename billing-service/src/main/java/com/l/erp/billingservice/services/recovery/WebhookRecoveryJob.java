package com.l.erp.billingservice.services.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.billingservice.domain.WebhookLog;
import com.l.erp.billingservice.infra.asaas.dto.AsaasWebhookPayload;
import com.l.erp.billingservice.infra.redis.DistributedLockService;
import com.l.erp.billingservice.repository.WebhookLogRepository;
import com.l.erp.billingservice.services.webhook.WebhookProcessor;
import com.l.erp.common.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Recupera webhooks presos em {@code RECEBIDO} (Fase 7 — spec §28.x): se o processamento async
 * não finalizou (markProcessed/markError) em >10min, reprocessa o payload. A idempotência (Redis,
 * no {@link WebhookProcessor}) garante que um evento já efetivado não rode de novo.
 */
@Component
public class WebhookRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(WebhookRecoveryJob.class);
    private static final int STUCK_MINUTES = 10;

    // ObjectMapper próprio com módulos (LocalDate em AsaasPaymentData) — evita ambiguidade de bean.
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final DistributedLockService lockService;
    private final WebhookLogRepository webhookLogRepository;
    private final WebhookProcessor webhookProcessor;

    public WebhookRecoveryJob(DistributedLockService lockService,
                              WebhookLogRepository webhookLogRepository,
                              WebhookProcessor webhookProcessor) {
        this.lockService = lockService;
        this.webhookLogRepository = webhookLogRepository;
        this.webhookProcessor = webhookProcessor;
    }

    @Scheduled(cron = "${billing.cron.webhook-recovery}")
    public void run() {
        String lockKey = "syax:billing:lock:webhook-recovery";
        String lockOwner = UUID.randomUUID().toString();
        if (!lockService.acquire(lockKey, lockOwner, 600)) {
            log.info("Lock de webhook-recovery já adquirido — pulando");
            return;
        }
        try {
            OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(STUCK_MINUTES);
            List<WebhookLog> stuck = webhookLogRepository
                    .findByStatusAndReceivedAtBefore(Constants.WEBHOOK_RECEBIDO, cutoff);
            if (stuck.isEmpty()) {
                return;
            }
            log.warn("WebhookRecovery: {} webhook(s) presos em RECEBIDO — reprocessando", stuck.size());
            for (WebhookLog wl : stuck) {
                try {
                    AsaasWebhookPayload payload = MAPPER.readValue(wl.getPayload(), AsaasWebhookPayload.class);
                    webhookProcessor.processAsync(payload, wl);
                } catch (Exception e) {
                    log.error("Falha ao reprocessar webhook_log id={}", wl.getId(), e);
                }
            }
        } finally {
            lockService.release(lockKey, lockOwner);
        }
    }
}
