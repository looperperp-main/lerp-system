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
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Classe de configuração do Spring para consumidores do Apache Kafka.
 * Define a conexão com o Kafka, as estratégias de resiliência (tratamento de erros e retentativas)
 * e o mecanismo para chamadas síncronas (Request/Reply) utilizando tópicos.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    /**
     * Nome do tópico Kafka utilizado para receber as respostas (replies) relacionadas às consultas de extrato.
     */
    public static final String EXTRATO_REPLY_TOPIC = "partner.extrato.reply";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Configura a fábrica de consumidores do Kafka.
     * Define o endereço do servidor, o grupo de consumidores padrão, a estratégia de leitura
     * de offsets e os desserializadores de chaves e valores.
     *
     * @return a fábrica de consumidores configurada.
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "partner-service-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var deser = new StringDeserializer();
        return new DefaultKafkaConsumerFactory<>(props, deser, deser);
    }

    /**
     * Template Kafka para envio de eventos com valor serializado como JSON.
     * Declarado explicitamente porque a auto-configuração do Spring Boot é suprimida
     * quando replyingKafkaTemplate (subclasse de KafkaTemplate) já existe como bean.
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    /**
     * Configura o tratador de erros padrão para os listeners do Kafka.
     * Implementa um mecanismo de Dead Letter Topic (DLT) para mensagens com falha e
     * uma estratégia de retentativas (BackOff) com "Jitter" para evitar sobrecarga.
     * Exceções de processamento de JSON ({@link JsonProcessingException}) não são retentadas.
     *
     * @param kafkaTemplate o template usado para enviar mensagens falhas ao DLT.
     * @return o tratador de erros configurado.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (r, e) -> new TopicPartition(r.topic() + ".DLT", -1));
        var handler = new DefaultErrorHandler(recoverer, jitteredBackOff());
        handler.addNotRetryableExceptions(JsonProcessingException.class);
        return handler;
    }

    /**
     * Implementa uma estratégia de retentativas com recuo exponencial e "Jitter" (variação aleatória).
     * Realiza no máximo 3 tentativas, variando aleatoriamente o tempo de espera com um teto de 1s, 2s e 4s.
     * Isso ajuda a evitar o efeito "Thundering Herd" ao processar falhas.
     *
     * @return a estratégia de BackOff configurada.
     */
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

    /**
     * Configura a fábrica de containers para os listeners ({@code @KafkaListener}).
     * Associa a fábrica de consumidores e o tratador de erros customizado a todos os listeners criados.
     *
     * @param kafkaErrorHandler o tratador de erros padrão configurado.
     * @return a fábrica de containers para os listeners do Kafka.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    /**
     * Configura um template para enviar requisições e aguardar respostas no Kafka (padrão Request/Reply).
     * Cria um produtor interno dedicado para evitar conflitos de serialização com outros beans
     * e configura um listener container específico para escutar o tópico de respostas.
     *
     * @param containerFactory a fábrica de containers para criar o listener de respostas.
     * @return o template de Request/Reply configurado.
     */
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