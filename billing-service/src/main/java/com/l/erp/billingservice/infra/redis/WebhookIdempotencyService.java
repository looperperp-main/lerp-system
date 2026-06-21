package com.l.erp.billingservice.infra.redis;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Barreira RÁPIDA de idempotência de webhooks via Redis (spec payments-service.md §6.8).
 *
 * <p>{@link #tryAcquire} marca o evento como "em processamento" atomicamente: retorna
 * {@code true} apenas na primeira vez. Erros transitórios devem chamar {@link #release}
 * para que a retentativa do Asaas reprocesse; erros permanentes mantêm a chave via
 * {@link #markError}. A barreira DEFINITIVA é o UNIQUE em {@code billing.commission.asaas_payment_id}
 * e {@code billing.webhook_log.asaas_event_id}.</p>
 */
@Service
public class WebhookIdempotencyService {

    private final RedisTemplate<String, String> redis;
    private final RedisScript<Long> acquireScript;
    private final RedisScript<Long> completeScript;

    @Value("${billing.redis.webhook-idempotency-ttl-seconds:86400}")
    private long ttl;

    public WebhookIdempotencyService(RedisTemplate<String, String> redis,
                                     @Qualifier("webhookIdempotencyScript") RedisScript<Long> acquireScript,
                                     @Qualifier("webhookCompleteScript") RedisScript<Long> completeScript) {
        this.redis = redis;
        this.acquireScript = acquireScript;
        this.completeScript = completeScript;
    }

    public boolean tryAcquire(String eventType, String asaasEventId) {
        String key = buildKey(eventType, asaasEventId);
        Long result = redis.execute(acquireScript,
                Collections.singletonList(key),
                String.valueOf(ttl));
        return Long.valueOf(1L).equals(result);
    }

    public void markDone(String eventType, String asaasEventId) {
        markFinal(eventType, asaasEventId, "DONE");
    }

    public void markError(String eventType, String asaasEventId) {
        markFinal(eventType, asaasEventId, "ERROR");
    }

    /** Erro transitório: apaga a chave para a retentativa do Asaas reprocessar. */
    public void release(String eventType, String asaasEventId) {
        redis.delete(buildKey(eventType, asaasEventId));
    }

    private void markFinal(String eventType, String asaasEventId, String status) {
        String key = buildKey(eventType, asaasEventId);
        redis.execute(completeScript,
                Collections.singletonList(key),
                status, String.valueOf(ttl));
    }

    private String buildKey(String eventType, String asaasEventId) {
        return String.format("syax:billing:webhook:%s:%s", eventType, asaasEventId);
    }
}