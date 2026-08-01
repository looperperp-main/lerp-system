package com.l.erp.common.infra.kafka;

import com.l.erp.common.util.Constants;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Reidrata o {@code correlationId} no MDC da thread do listener a partir do header carimbado pelo
 * {@link CorrelationIdProducerInterceptor}. É o equivalente do {@code CorrelationIdFilter} para o
 * lado do Kafka — sem ele toda linha de log de consumidor sai com {@code []}.
 *
 * <p>Se a mensagem vier sem header (produtor antigo, ou publicada fora de um request), gera um id
 * novo: melhor um rastro próprio do que nenhum.</p>
 */
public class CorrelationIdRecordInterceptor implements RecordInterceptor<String, String> {

    @Override
    public ConsumerRecord<String, String> intercept(ConsumerRecord<String, String> record,
                                                    Consumer<String, String> consumer) {
        Header header = record.headers().lastHeader(Constants.HEADER_CORRELATION_ID);
        String correlationId = header != null
                ? new String(header.value(), StandardCharsets.UTF_8)
                : UUID.randomUUID().toString();
        MDC.put(Constants.MDC_CORRELATION_ID, correlationId);
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
        // A thread do listener é reaproveitada — sem isso o próximo registro herda o id do anterior.
        MDC.remove(Constants.MDC_CORRELATION_ID);
    }
}
