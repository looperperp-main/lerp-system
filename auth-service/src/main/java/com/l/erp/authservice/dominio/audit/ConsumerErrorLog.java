package com.l.erp.authservice.dominio.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * DLQ de consumidores Kafka: registra o evento cru que um listener não conseguiu processar,
 * em vez de perdê-lo no commit do offset. Reprocessamento é manual/agendado a partir desta tabela.
 */
@Getter
@Setter
@Entity
@Table(name = "consumer_error_log", schema = "audit")
public class ConsumerErrorLog {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "error_message", nullable = false, length = Integer.MAX_VALUE)
    private String errorMessage;

    @Column(name = "payload_json", nullable = false, length = Integer.MAX_VALUE)
    private String payloadJson;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}