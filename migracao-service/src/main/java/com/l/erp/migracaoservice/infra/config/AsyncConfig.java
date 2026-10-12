package com.l.erp.migracaoservice.infra.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/** Executores limitados do export e do import: poucas tarefas em paralelo para não esgotar o pool do Hikari (8). */
@Configuration
public class AsyncConfig {

    public static final String EXPORT_EXECUTOR = "exportExecutor";
    public static final String IMPORT_EXECUTOR = "importExecutor";

    @Bean(name = EXPORT_EXECUTOR)
    public Executor exportExecutor() {
        return executor("export-");
    }

    /** Validação do staging (e, adiante, a promoção): lê e grava em lotes, então 2 threads bastam. */
    @Bean(name = IMPORT_EXECUTOR)
    public Executor importExecutor() {
        return executor("import-");
    }

    private static Executor executor(String prefixo) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(2);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix(prefixo);
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
