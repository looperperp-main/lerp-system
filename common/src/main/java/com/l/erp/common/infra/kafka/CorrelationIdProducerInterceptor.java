package com.l.erp.common.infra.kafka;

import com.l.erp.common.util.Constants;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Carimba o {@code correlationId} do MDC como header Kafka na saída, para o consumidor do outro
 * lado poder reidratá-lo ({@link CorrelationIdRecordInterceptor}). Sem isso o rastro morre na
 * fronteira do tópico: o login e a auditoria dele viram dois eventos sem parentesco.
 *
 * <p>Instanciado pelo próprio Kafka via {@code ProducerConfig.INTERCEPTOR_CLASSES_CONFIG} — precisa
 * de construtor sem argumentos e não recebe injeção do Spring (o MDC é estático, então tudo bem).</p>
 */
public class CorrelationIdProducerInterceptor implements ProducerInterceptor<Object, Object> {

    @Override
    public ProducerRecord<Object, Object> onSend(ProducerRecord<Object, Object> record) {
        String correlationId = MDC.get(Constants.MDC_CORRELATION_ID);
        if (correlationId != null
                && !correlationId.isBlank()
                && record.headers().lastHeader(Constants.HEADER_CORRELATION_ID) == null) {
            record.headers().add(Constants.HEADER_CORRELATION_ID,
                    correlationId.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // ponytail: nada a fazer — o carimbo acontece no onSend
    }

    @Override
    public void close() {
    }

    @Override
    public void configure(Map<String, ?> configs) {
    }
}
