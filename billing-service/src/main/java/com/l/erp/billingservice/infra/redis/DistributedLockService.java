package com.l.erp.billingservice.infra.redis;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Lock distribuído via Redis para cron jobs (spec payments-service.md §13.4 / ADR-003).
 *
 * <p>Cada job tenta adquirir o lock antes de executar; se falhar (outra instância já
 * está rodando), encerra silenciosamente. Garante que jobs não rodem em paralelo em
 * deploy multi-instância.</p>
 */
@Service
public class DistributedLockService {

    private final RedisTemplate<String, String> redis;
    private final RedisScript<Long> acquireScript;
    private final RedisScript<Long> releaseScript;

    public DistributedLockService(
        RedisTemplate<String, String> redis,
        @Qualifier("acquireLockScript") RedisScript<Long> acquireScript,
        @Qualifier("releaseLockScript") RedisScript<Long> releaseScript
    ) {
        this.redis = redis;
        this.acquireScript = acquireScript;
        this.releaseScript = releaseScript;
    }

    /**
     * Tenta adquirir o lock.
     * @param key chave do lock (ex: {@code syax:billing:lock:commission-payout:2025-01})
     * @param owner identificador único da instância/thread (UUID)
     * @param ttlSeconds tempo de vida do lock
     * @return true se adquiriu, false se já está travado por outra instância
     */
    public boolean acquire(String key, String owner, long ttlSeconds) {
        Long result = redis.execute(acquireScript,
                Collections.singletonList(key),
                owner, String.valueOf(ttlSeconds));
        return Long.valueOf(1L).equals(result);
    }

    /**
     * Libera o lock — apenas se a instância atual for a dona.
     * @return true se liberou, false se não é dona ou já expirou
     */
    public boolean release(String key, String owner) {
        Long result = redis.execute(releaseScript,
                Collections.singletonList(key),
                owner);
        return Long.valueOf(1L).equals(result);
    }
}