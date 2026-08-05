package com.l.erp.billingservice.infra.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Thread pools para processamento assíncrono de webhooks (spec §8.8 / ADR-001).
 * O controller retorna 200 em &lt;100ms; o processamento real roda no {@code webhookExecutor}.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("webhookExecutor")
    public Executor webhookExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("webhook-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(mdcDecorator());
        executor.initialize();
        return executor;
    }

    @Bean("commissionExecutor")
    public Executor commissionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("commission-async-");
        executor.setTaskDecorator(mdcDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * Copia o MDC da thread que submete (a do request, populada pelo {@code CorrelationIdFilter})
     * para a thread do pool. Sem isso o {@code correlationId} some justamente no processamento
     * assíncrono — que é onde está tudo que interessa rastrear — e a linha sai com {@code []}.
     */
    private static TaskDecorator mdcDecorator() {
        return task -> {
            Map<String, String> contexto = MDC.getCopyOfContextMap();
            return () -> {
                if (contexto != null) {
                    MDC.setContextMap(contexto);
                }
                try {
                    task.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }
}