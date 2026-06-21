package com.l.erp.billingservice.infra.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Cache do status do tenant no Redis (spec payments-service.md §6.1, §11.2).
 *
 * <p><b>Nota arquitetural:</b> o endpoint síncrono {@code GET /internal/billing/status}
 * (spec §11) foi <b>descartado por decisão de resiliência</b> — o auth-service mantém o
 * estado de acesso localmente e é atualizado por Kafka, então login não depende do billing
 * de pé. Esta classe permanece como infraestrutura de cache write-through usada pelos
 * handlers/jobs (ex.: ativação, dunning) para leituras internas. Armazena o status como
 * {@code String} para alinhar com {@code Subscription.status} (que hoje é String no repo).</p>
 */
@Service
public class TenantStatusCacheService {

    private static final Logger log = LoggerFactory.getLogger(TenantStatusCacheService.class);

    private final RedisTemplate<String, String> redis;

    @Value("${billing.redis.tenant-status-cache-ttl-seconds:300}")
    private long ttl;

    public TenantStatusCacheService(RedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    public Optional<String> get(Long tenantId) {
        return Optional.ofNullable(redis.opsForValue().get(buildKey(tenantId)));
    }

    /** Write-through: o caller conhece o novo status, então populamos direto (nunca evict). */
    public void put(Long tenantId, String status) {
        try {
            redis.opsForValue().set(buildKey(tenantId), status, Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("Falha ao cachear status do tenant {}", tenantId, e);
        }
    }

    public void evict(Long tenantId) {
        redis.delete(buildKey(tenantId));
    }

    private String buildKey(Long tenantId) {
        return "syax:billing:tenant:status:" + tenantId;
    }
}