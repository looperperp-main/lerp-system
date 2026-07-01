package com.l.erp.authservice.services.audit;

import com.l.erp.authservice.dominio.audit.ConsumerErrorLog;
import com.l.erp.authservice.repositorios.audit.ConsumerErrorLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Grava na DLQ ({@code audit.consumer_error_log}) o evento cru que um consumidor Kafka não
 * conseguiu processar. {@link Propagation#REQUIRES_NEW} garante que o registro persiste mesmo
 * quando a transação do processamento sofreu rollback.
 */
@Service
public class ConsumerErrorLogService {

    private static final Logger log = LoggerFactory.getLogger(ConsumerErrorLogService.class);

    private final ConsumerErrorLogRepository repository;

    public ConsumerErrorLogService(ConsumerErrorLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String serviceName, String topic, String payloadJson, Throwable error) {
        try {
            ConsumerErrorLog entry = new ConsumerErrorLog();
            entry.setServiceName(serviceName);
            entry.setTopic(topic);
            entry.setPayloadJson(payloadJson != null ? payloadJson : "");
            entry.setErrorMessage(error != null ? String.valueOf(error) : "unknown");
            entry.setCreatedAt(Instant.now());
            repository.save(entry);
        } catch (Exception e) {
            // Último recurso: se nem a DLQ grava, ao menos o evento fica no log da aplicação.
            log.error("Falha ao gravar na DLQ consumer_error_log — topic={} payload={}", topic, payloadJson, e);
        }
    }
}