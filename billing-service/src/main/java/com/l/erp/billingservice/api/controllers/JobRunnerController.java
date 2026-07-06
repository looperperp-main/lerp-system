package com.l.erp.billingservice.api.controllers;

import com.l.erp.billingservice.services.dunning.DunningJob;
import com.l.erp.billingservice.services.payout.CommissionPayoutJob;
import com.l.erp.billingservice.services.recovery.ReconciliationJob;
import com.l.erp.billingservice.services.recovery.WebhookRecoveryJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runner de jobs agendados (diagnóstico #5): dispara manualmente os crons de billing e mostra
 * o resultado da última execução manual. Cada job já é idempotente e tem lock distribuído próprio.
 * Gate: {@code REPASSE_EXECUTE} (scope PLATFORM, só admin Syax) — mesma authority do trigger de repasse.
 * ponytail: só histórico de execução MANUAL (in-memory); execuções agendadas não aparecem aqui.
 */
@RestController
@RequestMapping("/api/v1/diagnostics/jobs")
@PreAuthorize("hasAuthority('REPASSE_EXECUTE')")
public class JobRunnerController {

    private static final Logger log = LoggerFactory.getLogger(JobRunnerController.class);

    public record JobInfo(String key, String label, String lastRun, String lastStatus, Long lastDurationMs) {}

    private record Job(String label, Runnable action) {}

    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final Map<String, JobInfo> lastRuns = new ConcurrentHashMap<>();

    public JobRunnerController(ReconciliationJob reconciliation,
                              WebhookRecoveryJob webhookRecovery,
                              DunningJob dunning,
                              CommissionPayoutJob commissionPayout) {
        jobs.put("reconciliation", new Job("Reconciliação de pagamentos", reconciliation::run));
        jobs.put("webhook-recovery", new Job("Recuperação de webhooks presos", webhookRecovery::run));
        jobs.put("dunning", new Job("Dunning (cobrança/suspensão)", dunning::run));
        jobs.put("commission-payout", new Job("Repasse de comissões", commissionPayout::run));
    }

    @GetMapping
    public ResponseEntity<List<JobInfo>> list() {
        List<JobInfo> result = jobs.entrySet().stream()
                .map(e -> lastRuns.getOrDefault(e.getKey(),
                        new JobInfo(e.getKey(), e.getValue().label(), null, null, null)))
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{key}/run")
    public ResponseEntity<JobInfo> run(@PathVariable String key) {
        Job job = jobs.get(key);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job desconhecido: " + key);
        }
        log.info("Disparo manual do job {} (admin)", key);
        lastRuns.put(key, new JobInfo(key, job.label(), Instant.now().toString(), "EXECUTANDO", null));
        // ponytail: assíncrono — reconciliation/dunning varrem várias entidades e chamam o Asaas; rodar
        // no thread HTTP estouraria o timeout. Um pool dedicado só se muitos jobs concorrerem.
        CompletableFuture.runAsync(() -> {
            Instant start = Instant.now();
            try {
                job.action().run();
                lastRuns.put(key, new JobInfo(key, job.label(), start.toString(), "OK",
                        Duration.between(start, Instant.now()).toMillis()));
            } catch (Exception e) {
                log.error("Job manual {} falhou", key, e);
                lastRuns.put(key, new JobInfo(key, job.label(), start.toString(), "ERRO: " + e.getMessage(),
                        Duration.between(start, Instant.now()).toMillis()));
            }
        });
        return ResponseEntity.accepted().body(lastRuns.get(key));
    }
}
