package com.l.erp.migracaoservice.infra.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/** Executor limitado do export: poucas extrações em paralelo para não esgotar o pool do Hikari (8). */
@Configuration
public class AsyncConfig {

    public static final String EXPORT_EXECUTOR = "exportExecutor";

    @Bean(name = EXPORT_EXECUTOR)
    public Executor exportExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(2);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix("export-");
        // Leva o correlationId do request para a thread da extração (mesmo padrão do billing-service).
        ex.setTaskDecorator(task -> {
            Map<String, String> contexto = MDC.getCopyOfContextMap();
            return () -> {
                if (contexto != null) MDC.setContextMap(contexto);
                try {
                    task.run();
                } finally {
                    MDC.clear();
                }
            };
        });
        // Fila cheia: o pedido já está gravado como REQUESTED; roda quem chamou em vez de perder o job.
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();
        return ex;
    }
}
