package com.l.erp.partnerservice.infra.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.partnerservice.infra.config.KafkaConsumerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class BillingClient {

    private static final Logger log = LoggerFactory.getLogger(BillingClient.class);
    private static final String EXTRATO_TOPIC = "partner.extrato.request";
    private static final String ASSINATURA_TOPIC = "partner.assinatura.request";

    private final ReplyingKafkaTemplate<String, String, String> replyingKafkaTemplate;
    private final ObjectMapper objectMapper;

    public BillingClient(ReplyingKafkaTemplate<String, String, String> replyingKafkaTemplate,
                         ObjectMapper objectMapper) {
        this.replyingKafkaTemplate = replyingKafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * @throws IllegalStateException se o billing-service não responder (timeout/erro Kafka) —
     *         quem chamar decide se propaga o erro ou trata como indisponibilidade parcial.
     */
    public BillingExtratoDTO getExtrato(UUID partnerId) {
        return request(EXTRATO_TOPIC, partnerId.toString(), BillingExtratoDTO.class, "extrato de comissões");
    }

    /**
     * @throws IllegalStateException se o billing-service não responder (timeout/erro Kafka).
     */
    public BillingAssinaturaResumoDTO getAssinaturaResumo(Long tenantId) {
        return request(ASSINATURA_TOPIC, tenantId.toString(), BillingAssinaturaResumoDTO.class, "resumo de assinatura");
    }

    private <T> T request(String topic, String key, Class<T> responseType, String descricao) {
        log.info("Consultando {} via Kafka (topic={}, key={})", descricao, topic, key);
        try {
            var producerRecord = new ProducerRecord<String, String>(topic, key, key);
            producerRecord.headers().add(KafkaHeaders.REPLY_TOPIC,
                    KafkaConsumerConfig.EXTRATO_REPLY_TOPIC.getBytes(StandardCharsets.UTF_8));

            var future = replyingKafkaTemplate.sendAndReceive(producerRecord);
            var response = future.get(10, TimeUnit.SECONDS);
            return objectMapper.readValue(response.value(), responseType);
        } catch (InterruptedException e) {
            // Só aqui faz sentido restaurar a flag (a espera foi realmente interrompida).
            log.error("Consulta de {} interrompida (key={})", descricao, key, e);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Consulta de " + descricao + " interrompida", e);
        } catch (Exception e) {
            // TimeoutException/ExecutionException: NUNCA setar a flag de interrupção na thread web
            // aqui — ela envenena o flush da resposta (ClientAbortException → 500).
            log.error("Falha ao consultar {} via Kafka (key={})", descricao, key, e);
            throw new IllegalStateException("billing-service indisponível para consulta de " + descricao, e);
        }
    }
}