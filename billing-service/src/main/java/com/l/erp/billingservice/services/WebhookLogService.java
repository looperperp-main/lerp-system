package com.l.erp.billingservice.services;

import com.l.erp.billingservice.domain.WebhookLog;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import com.l.erp.billingservice.repository.WebhookLogRepository;
import com.l.erp.common.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Persistência e ciclo de vida do {@code billing.webhook_log} (spec §8.1, §28.5).
 *
 * <p>{@link #logReceived} grava o recebimento (status RECEBIDO) ANTES do processamento async
 * e <b>tolera duplicata</b> de {@code asaas_event_id}: na retentativa do Asaas, o UNIQUE
 * {@code uq_webhook_log_event_id} dispara — tratamos como duplicata benigna e devolvemos o
 * registro existente. Nunca deixamos a exceção vazar para o controller, pois resposta não-2xx
 * pausa a fila inteira do Asaas (§28.7).</p>
 */
@Service
public class WebhookLogService {

    private static final Logger log = LoggerFactory.getLogger(WebhookLogService.class);

    private final WebhookLogRepository webhookLogRepository;
    private final SubscriptionRepository subscriptionRepository;

    public WebhookLogService(WebhookLogRepository webhookLogRepository,
                             SubscriptionRepository subscriptionRepository) {
        this.webhookLogRepository = webhookLogRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * Grava o recebimento do webhook em transação própria (REQUIRES_NEW) — um conflito de
     * idempotência não deve poluir a transação do chamador.
     *
     * @return o log recém-criado, ou o existente caso o evento já tenha sido recebido antes
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WebhookLog logReceived(String eventType, String asaasEventId, String asaasPaymentId,
                                  String asaasSubscriptionId, String rawPayload) {

        // Pré-check: cobre o caso comum sem gerar exceção
        if (asaasEventId != null) {
            var existing = webhookLogRepository.findByAsaasEventId(asaasEventId);
            if (existing.isPresent()) {
                log.info("Webhook duplicado (asaasEventId={}) — retornando log existente", asaasEventId);
                return existing.get();
            }
        }

        WebhookLog webhookLog = new WebhookLog();
        webhookLog.setEventType(eventType != null ? eventType : "ASAAS_WEBHOOK");
        webhookLog.setAsaasEventId(asaasEventId);
        webhookLog.setAsaasPaymentId(asaasPaymentId);
        webhookLog.setAsaasSubscriptionId(asaasSubscriptionId);
        // tenant_id não vem no payload do Asaas — resolvido pela subscription quando disponível
        if (asaasSubscriptionId != null) {
            subscriptionRepository.findByAsaasSubscriptionId(asaasSubscriptionId)
                    .ifPresent(s -> webhookLog.setTenantId(s.getTenantId()));
        }
        webhookLog.setPayload(rawPayload);
        webhookLog.setStatus(Constants.WEBHOOK_RECEBIDO);
        webhookLog.setReceivedAt(OffsetDateTime.now());

        try {
            return webhookLogRepository.save(webhookLog);
        } catch (DataIntegrityViolationException e) {
            // Corrida: outra entrega gravou o mesmo asaas_event_id entre o pré-check e o save
            log.info("Conflito de idempotência no webhook_log (asaasEventId={}) — duplicata benigna", asaasEventId);
            return webhookLogRepository.findByAsaasEventId(asaasEventId).orElseThrow(() -> e);
        }
    }

    @Transactional
    public void markProcessed(WebhookLog webhookLog) {
        webhookLog.setStatus(Constants.WEBHOOK_PROCESSADO);
        webhookLog.setProcessedAt(OffsetDateTime.now());
        webhookLogRepository.save(webhookLog);
    }

    @Transactional
    public void markIgnored(WebhookLog webhookLog, String reason) {
        webhookLog.setStatus(Constants.IGNORADO);
        webhookLog.setErrorMessage(reason);
        webhookLog.setProcessedAt(OffsetDateTime.now());
        webhookLogRepository.save(webhookLog);
    }

    @Transactional
    public void markError(WebhookLog webhookLog, String errorMessage) {
        webhookLog.setStatus(Constants.WEBHOOK_ERRO);
        webhookLog.setErrorMessage(errorMessage);
        webhookLog.setProcessedAt(OffsetDateTime.now());
        webhookLogRepository.save(webhookLog);
    }
}