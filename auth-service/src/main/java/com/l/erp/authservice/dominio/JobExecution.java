package com.l.erp.authservice.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Uma execução de job agendado do auth (schedulers de trial D+10/D+15). Espelha
 * billing.job_execution: uma linha por execução, manual (botão do admin) ou cron. Substitui o
 * cache in-memory do DiagnosticsController, que perdia tudo no restart.
 */
@Getter
@Setter
@Entity
@Table(name = "job_execution", schema = "auth")
public class JobExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "job_key", nullable = false, length = 60)
    private String jobKey;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", length = Integer.MAX_VALUE)
    private String errorMessage;
}
