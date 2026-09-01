package com.l.erp.billingservice.services;

import com.l.erp.billingservice.domain.JobExecution;
import com.l.erp.billingservice.repository.JobExecutionRepository;
import com.l.erp.common.util.Constants;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Gauge {@code job_segundos_desde_ok{job="..."}}: há quanto tempo cada job agendado não termina
 * com sucesso.
 *
 * <p>Pega a falha silenciosa que nenhum contador de erro pega — o job que <b>parou de rodar</b>.
 * Um job morto não gera erro, não gera log, não gera nada: só para. Este número crescendo sem
 * teto é o único sinal disso.</p>
 *
 * <p>Alerta sugerido: {@code job_segundos_desde_ok{job="webhook-recovery"} > 3600}.</p>
 */
@Component
public class JobMetrics {

    /** Os quatro jobs registrados via JobExecutionRecorder no billing (ver JobRunnerController). */
    private static final List<String> JOBS = List.of(
            Constants.JOB_KEY_RECONCILIATION,
            Constants.JOB_KEY_WEBHOOK_RECOVERY,
            Constants.JOB_KEY_DUNNING,
            Constants.JOB_KEY_COMMISSION_PAYOUT);

    public JobMetrics(JobExecutionRepository repository, MeterRegistry meterRegistry) {
        for (String jobKey : JOBS) {
            Gauge.builder(Constants.METRIC_JOB_SEGUNDOS_DESDE_OK, repository,
                            r -> segundosDesdeOk(r, jobKey))
                    .tag(Constants.METRIC_TAG_JOB, jobKey)
                    .description("Segundos desde a última execução OK; "
                            + Constants.METRIC_JOB_NUNCA_EXECUTADO + " = nunca teve sucesso")
                    .register(meterRegistry);
        }
    }

    // ponytail: uma consulta por job por scrape (4 × 4/min). Se um dia pesar, um único
    // "SELECT job_key, max(finished_at) ... GROUP BY job_key" resolve os quatro de uma vez.
    private static double segundosDesdeOk(JobExecutionRepository repository, String jobKey) {
        return repository
                .findFirstByJobKeyAndStatusOrderByFinishedAtDesc(jobKey, Constants.JOB_STATUS_OK)
                .map(JobExecution::getFinishedAt)
                .map(fim -> (double) Duration.between(fim, OffsetDateTime.now()).getSeconds())
                .orElse(Constants.METRIC_JOB_NUNCA_EXECUTADO);
    }
}
