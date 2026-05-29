package com.l.erp.partnerservice.infra.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    public static final String EXTRATO_REPLY_TOPIC = "partner.extrato.reply";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "partner-service-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var deser = new StringDeserializer();
        return new DefaultKafkaConsumerFactory<>(props, deser, deser);
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (r, e) -> new TopicPartition(r.topic() + ".DLT", -1));
        var handler = new DefaultErrorHandler(recoverer, jitteredBackOff());
        handler.addNotRetryableExceptions(JsonProcessingException.class);
        return handler;
    }

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
        return factory;
    }

    @Bean
    public ReplyingKafkaTemplate<String, String, String> replyingKafkaTemplate(
            ConcurrentKafkaListenerContainerFactory<String, String> containerFactory) {
        // ProducerFactory local — não registrado como bean para não conflitar
        // com o auto-configurado (JsonSerializer) usado pelo KafkaPartnerProducerService
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        var pf = new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new StringSerializer());

        var repliesContainer = containerFactory.createContainer(EXTRATO_REPLY_TOPIC);
        repliesContainer.getContainerProperties().setGroupId("partner-extrato-reply-group");
        repliesContainer.setAutoStartup(false);

        var template = new ReplyingKafkaTemplate<>(pf, repliesContainer);
        template.setDefaultReplyTimeout(Duration.ofSeconds(10));
        return template;
    }
}