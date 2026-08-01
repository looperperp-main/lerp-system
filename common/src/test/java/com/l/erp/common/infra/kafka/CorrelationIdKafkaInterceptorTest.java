package com.l.erp.common.infra.kafka;

import com.l.erp.common.util.Constants;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdKafkaInterceptorTest {

    private final CorrelationIdProducerInterceptor producer = new CorrelationIdProducerInterceptor();
    private final CorrelationIdRecordInterceptor consumidor = new CorrelationIdRecordInterceptor();

    @AfterEach
    void limparMdc() {
        MDC.clear();
    }

    private static ProducerRecord<Object, Object> registroDeSaida() {
        return new ProducerRecord<>("auth.audit", "chave", "{\"evento\":\"LOGIN\"}");
    }

    private static ConsumerRecord<String, String> registroDeEntrada() {
        return new ConsumerRecord<>("auth.audit", 0, 0L, "chave", "{\"evento\":\"LOGIN\"}");
    }

    private static String header(ProducerRecord<Object, Object> record) {
        var h = record.headers().lastHeader(Constants.HEADER_CORRELATION_ID);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    @Test
    void produtor_comMdcPopulado_carimbaHeader() {
        MDC.put(Constants.MDC_CORRELATION_ID, "abc-123");

        var record = producer.onSend(registroDeSaida());

        assertThat(header(record)).isEqualTo("abc-123");
    }

    @Test
    void produtor_semMdc_naoCarimba() {
        var record = producer.onSend(registroDeSaida());

        assertThat(header(record)).isNull();
    }

    @Test
    void consumidor_comHeader_reidrataOMesmoId() {
        var record = registroDeEntrada();
        record.headers().add(Constants.HEADER_CORRELATION_ID, "abc-123".getBytes(StandardCharsets.UTF_8));

        consumidor.intercept(record, null);

        assertThat(MDC.get(Constants.MDC_CORRELATION_ID)).isEqualTo("abc-123");
    }

    @Test
    void consumidor_semHeader_geraIdNovo() {
        consumidor.intercept(registroDeEntrada(), null);

        assertThat(MDC.get(Constants.MDC_CORRELATION_ID)).isNotBlank();
    }

    @Test
    void consumidor_afterRecord_limpaParaNaoVazarNaProximaMensagem() {
        var record = registroDeEntrada();
        record.headers().add(Constants.HEADER_CORRELATION_ID, "abc-123".getBytes(StandardCharsets.UTF_8));
        consumidor.intercept(record, null);

        consumidor.afterRecord(record, null);

        assertThat(MDC.get(Constants.MDC_CORRELATION_ID)).isNull();
    }

    /** Ponta a ponta: o que o produtor carimba é exatamente o que o consumidor reidrata. */
    @Test
    void produtorEConsumidor_fecham_oCiclo() {
        MDC.put(Constants.MDC_CORRELATION_ID, "obs-teste-42");
        var saida = producer.onSend(registroDeSaida());
        MDC.clear();

        var entrada = registroDeEntrada();
        entrada.headers().add(Constants.HEADER_CORRELATION_ID,
                header(saida).getBytes(StandardCharsets.UTF_8));
        consumidor.intercept(entrada, null);

        assertThat(MDC.get(Constants.MDC_CORRELATION_ID)).isEqualTo("obs-teste-42");
    }
}
