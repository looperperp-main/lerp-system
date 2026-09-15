package com.l.erp.cadastroservice.infra.config;

import com.l.erp.common.infra.kafka.CorrelationIdRecordInterceptor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

import java.util.HashMap;
import java.util.Map;

/**
 * Primeiro consumer Kafka do cadastro-service (spec/p2p-compras.md §"Preço de compra", Fase 3) —
 * mesmo padrão minimalista do KafkaConsumerConfig do auth-service (sem request/reply, que só o
 * partner-service usa). Reaproveita o KafkaTemplate&lt;String, String&gt; já definido em
 * KafkaProducerConfig pro DLT.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "cadastro-service-group");
        StringDeserializer deser = new StringDeserializer();
        return new DefaultKafkaConsumerFactory<>(props, deser, deser);
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (r, e) -> new TopicPartition(r.topic() + ".DLT", -1));
        return new DefaultErrorHandler(recoverer, jitteredBackOff());
    }

    // Full-jitter: random [0, 2^attempt * 1s]. Evita thundering herd quando múltiplos consumers
    // falham ao mesmo tempo (mesmo padrão de auth-service/partner-service).
    private static BackOff jitteredBackOff() {
        return () -> new BackOffExecution() {
            private int attempt = 0;
            @Override
            public long nextBackOff() {
                if (attempt >= 3) return STOP;
                long ceiling = (1L << attempt++) * 1_000L;
                return (long) (Math.random() * ceiling);
            }
        };
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setRecordInterceptor(new CorrelationIdRecordInterceptor());
        return factory;
    }
}
