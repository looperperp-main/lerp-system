package com.l.erp.emissaofiscalservice.infra.config;

import com.l.erp.common.infra.kafka.CorrelationIdProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Mesmo padrão do cadastro-service/KafkaProducerConfig. O {@code KafkaTemplate} não é
 * autoconfigurado neste serviço, então o bean é declarado à mão (usado por OutboxPublisherJob e
 * AuditProducerService).
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // Carimba o correlationId do MDC como header — sem isso o rastro morre na fronteira do tópico
        configProps.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
                CorrelationIdProducerInterceptor.class.getName());

        StringSerializer stringSerializer = new StringSerializer();
        return new DefaultKafkaProducerFactory<>(configProps, stringSerializer, stringSerializer);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
