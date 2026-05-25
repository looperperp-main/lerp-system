package com.l.erp.authservice.infra.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaProducerConfig {
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // Agrupa mensagens por até 10ms antes de enviar — reduz overhead de rede sem impacto em latência perceptível
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        // Batch de 64KB (padrão 16KB) — eventos de auditoria são pequenos; batch maior reduz chamadas ao broker
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 65_536);
        // acks=1 — líder confirma antes de retornar; balanceia durabilidade e throughput para eventos de auditoria
        configProps.put(ProducerConfig.ACKS_CONFIG, "1");

        StringSerializer stringSerializer = new StringSerializer();
        return new DefaultKafkaProducerFactory<>(configProps, stringSerializer, stringSerializer);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
