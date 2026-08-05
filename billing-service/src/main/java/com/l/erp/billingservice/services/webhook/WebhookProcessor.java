package com.l.erp.billingservice.services.webhook;

import com.l.erp.billingservice.domain.WebhookLog;
import com.l.erp.billingservice.infra.asaas.dto.AsaasWebhookPayload;
import com.l.erp.billingservice.infra.exception.TransientException;
import com.l.erp.billingservice.infra.redis.WebhookIdempotencyService;
import com.l.erp.billingservice.services.WebhookLogService;
import com.l.erp.common.util.Constants;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Processa o webhook de forma assíncrona após o controller retornar 200 (spec §8.2, §28.1).
 *
 * <p>Pipeline: idempotência (Redis Lua) → roteamento (factory) → finalização. Erros
 * transitórios liberam a chave para a retentativa do Asaas; erros permanentes mantêm a
 * chave e marcam ERRO no {@code webhook_log}.</p>
 */
@Service
public class WebhookProcessor {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessor.class);

    private final WebhookIdempotencyService idempotencyService;
    private final WebhookHandlerFactory handlerFactory;
    private final WebhookLogService logService;
    private final MeterRegistry meterRegistry;

    public WebhookProcessor(WebhookIdempotencyService idempotencyService,
                            WebhookHandlerFactory handlerFactory,
                            WebhookLogService logService,
                            MeterRegistry meterRegistry) {
        this.idempotencyService = idempotencyService;
        this.handlerFactory = handlerFactory;
        this.logService = logService;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Conta o desfecho do webhook por tipo de evento.
     *
     * <p>{@code evento} é tag porque o Asaas tem um conjunto fechado e pequeno de eventos —
     * cardinalidade baixa. Id de pagamento/assinatura nunca vira tag (explodiria a série).</p>
     */
    private void contar(String eventType, String resultado) {
        Counter.builder(Constants.METRIC_WEBHOOK_PROCESSADO)
                .tag(Constants.METRIC_TAG_EVENTO, eventType == null ? "desconhecido" : eventType)
                .tag(Constants.METRIC_TAG_RESULTADO, resultado)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Chave de idempotência: {@code event.id} do Asaas (único por entrega — §28.2), com
     * fallback em cascata para payment/transfer/subscription id.
     */
    public static String resolveEventId(AsaasWebhookPayload payload) {
        if (payload.getId() != null) {
            return payload.getId();
        }
        if (payload.getPayment() != null && payload.getPayment().getId() != null) {
            return payload.getPayment().getId();
        }
        if (payload.getTransfer() != null && payload.getTransfer().getId() != null) {
            return payload.getTransfer().getId();
        }
        if (payload.getSubscription() != null && payload.getSubscription().getId() != null) {
            return payload.getSubscription().getId();
        }
        return UUID.randomUUID().toString();
    }

    @Async("webhookExecutor")
    public void processAsync(AsaasWebhookPayload payload, WebhookLog webhookLog) {
        String eventType = payload.getEvent();
        String eventId = resolveEventId(payload);

        // 1. Idempotência (barreira rápida)
        if (!idempotencyService.tryAcquire(eventType, eventId)) {
            log.info("Webhook duplicado ignorado — event={} id={}", eventType, eventId);
            contar(eventType, Constants.METRIC_RESULTADO_DUPLICADO);
            return;
        }

        try {
            Optional<WebhookEventHandler> handler = handlerFactory.getHandler(eventType);
            if (handler.isEmpty()) {
                idempotencyService.markDone(eventType, eventId);
                logService.markIgnored(webhookLog, "Sem handler para evento " + eventType);
                log.info("Webhook ignorado — sem handler para event={}", eventType);
                contar(eventType, Constants.METRIC_RESULTADO_IGNORADO);
                return;
            }

            handler.get().handle(payload);

            idempotencyService.markDone(eventType, eventId);
            logService.markProcessed(webhookLog);
            log.info("Webhook processado — event={} id={}", eventType, eventId);
            contar(eventType, Constants.METRIC_RESULTADO_OK);

        } catch (TransientException | TransientDataAccessException e) {
            // Transitório: libera a chave para a retentativa do Asaas reprocessar
            log.warn("Erro transitório webhook event={} id={} — chave liberada para retry", eventType, eventId, e);
            idempotencyService.release(eventType, eventId);
            logService.markError(webhookLog, "TRANSIENT: " + e.getMessage());
            contar(eventType, Constants.METRIC_RESULTADO_ERRO_TRANSITORIO);

        } catch (Exception e) {
            // Permanente: mantém a chave (não reprocessar lixo) e marca ERRO para investigação
            log.error("Erro permanente webhook event={} id={}", eventType, eventId, e);
            idempotencyService.markError(eventType, eventId);
            logService.markError(webhookLog, e.getMessage());
            contar(eventType, Constants.METRIC_RESULTADO_ERRO_PERMANENTE);
        }
    }
}