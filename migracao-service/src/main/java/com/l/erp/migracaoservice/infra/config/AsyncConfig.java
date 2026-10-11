package com.l.erp.migracaoservice.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
        // Fila cheia: o pedido já está gravado como REQUESTED; roda quem chamou em vez de perder o job.
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();
        return ex;
    }
}
