package com.l.erp.billingservice.api.controllers;

import com.l.erp.billingservice.repository.JobExecutionRepository;
import com.l.erp.billingservice.services.dunning.DunningJob;
import com.l.erp.billingservice.services.payout.CommissionPayoutJob;
import com.l.erp.billingservice.services.recovery.ReconciliationJob;
import com.l.erp.billingservice.services.recovery.WebhookRecoveryJob;
import com.l.erp.common.util.Constants;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Runner de jobs agendados (diagnóstico #5): dispara manualmente os crons de billing e mostra a
 * última execução. Cada job já é idempotente e tem lock distribuído próprio.
 * Gate: {@code REPASSE_EXECUTE} (scope PLATFORM, só admin Syax) — mesma authority do trigger de repasse.
 *
 * <p>A execução (início/fim/status/duração) é persistida pelo {@code JobExecutionRecorder} dentro
 * de cada job — captura tanto o disparo manual daqui quanto o cron agendado, e sobrevive a restart
 * (antes era um mapa in-memory que zerava e não somava entre réplicas).</p>
 */
@RestController
@RequestMapping("/api/v1/diagnostics/jobs")
@PreAuthorize("hasAuthority('REPASSE_EXECUTE')")
public class JobRunnerController {

    private static final Logger log = LoggerFactory.getLogger(JobRunnerController.class);

    public record JobInfo(String key, String label, String lastRun, String lastStatus, Long lastDurationMs) {}

    private record Job(String label, Runnable trigger) {}

    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final JobExecutionRepository executions;

    public JobRunnerController(ReconciliationJob reconciliation,
                              WebhookRecoveryJob webhookRecovery,
                              DunningJob dunning,
                              CommissionPayoutJob commissionPayout,
                              JobExecutionRepository executions) {
        this.executions = executions;
        jobs.put(Constants.JOB_KEY_RECONCILIATION, new Job("Reconciliação de pagamentos", reconciliation::run));
        jobs.put(Constants.JOB_KEY_WEBHOOK_RECOVERY, new Job("Recuperação de webhooks presos", webhookRecovery::run));
        jobs.put(Constants.JOB_KEY_DUNNING, new Job("Dunning (cobrança/suspensão)", dunning::run));
        jobs.put(Constants.JOB_KEY_COMMISSION_PAYOUT, new Job("Repasse de comissões", commissionPayout::run));
    }

    @GetMapping
    public ResponseEntity<List<JobInfo>> list() {
        List<JobInfo> result = jobs.entrySet().stream()
                .map(e -> executions.findFirstByJobKeyOrderByStartedAtDesc(e.getKey())
                        .map(x -> new JobInfo(e.getKey(), e.getValue().label(),
                                x.getStartedAt().toString(), x.getStatus(), x.getDurationMs()))
                        .orElse(new JobInfo(e.getKey(), e.getValue().label(), null, null, null)))
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
        // ponytail: assíncrono — reconciliation/dunning varrem várias entidades e chamam o Asaas; rodar
        // no thread HTTP estouraria o timeout. O recorder dentro do job persiste início/fim (manual e cron).
        CompletableFuture.runAsync(job.trigger());
        return ResponseEntity.accepted()
                .body(new JobInfo(key, job.label(), null, Constants.JOB_STATUS_RUNNING, null));
    }
}
