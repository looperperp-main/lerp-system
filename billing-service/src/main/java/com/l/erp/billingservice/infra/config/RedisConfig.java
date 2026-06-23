package com.l.erp.billingservice.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Configuração do Redis para o billing-service (spec payments-service.md §6.2).
 *
 * <p>Carrega os 5 scripts Lua de {@code src/main/resources/lua/} como beans
 * {@link RedisScript} no boot. Os scripts são a barreira RÁPIDA de idempotência/lock;
 * as constraints UNIQUE no banco são a barreira DEFINITIVA.</p>
 */
@Configuration
public class RedisConfig {

    @Bean
    @Primary
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> tpl = new RedisTemplate<>();
        tpl.setConnectionFactory(factory);
        tpl.setKeySerializer(new StringRedisSerializer());
        tpl.setValueSerializer(new StringRedisSerializer());
        tpl.setHashKeySerializer(new StringRedisSerializer());
        tpl.setHashValueSerializer(new StringRedisSerializer());
        return tpl;
    }

    @Bean("webhookIdempotencyScript")
    public RedisScript<Long> webhookIdempotencyScript() {
        return RedisScript.of(loadScript("lua/webhookIdempotencyAcquire.lua"), Long.class);
    }

    @Bean("webhookCompleteScript")
    public RedisScript<Long> webhookCompleteScript() {
        return RedisScript.of(loadScript("lua/webhookComplete.lua"), Long.class);
    }

    @Bean("acquireLockScript")
    public RedisScript<Long> acquireLockScript() {
        return RedisScript.of(loadScript("lua/acquireDistributedLock.lua"), Long.class);
    }

    @Bean("releaseLockScript")
    public RedisScript<Long> releaseLockScript() {
        return RedisScript.of(loadScript("lua/releaseDistributedLock.lua"), Long.class);
    }

    @Bean("annualGuardScript")
    public RedisScript<Long> annualGuardScript() {
        return RedisScript.of(loadScript("lua/annualCommissionGuard.lua"), Long.class);
    }

    private String loadScript(String path) {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource(path).getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível carregar script Lua: " + path, e);
        }
    }
}