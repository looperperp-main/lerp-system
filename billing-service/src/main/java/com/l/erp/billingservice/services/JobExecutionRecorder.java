package com.l.erp.billingservice.services;

import com.l.erp.billingservice.domain.JobExecution;
import com.l.erp.billingservice.repository.JobExecutionRepository;
import com.l.erp.common.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Grava início/fim/status/duração de uma execução de job em {@code billing.job_execution}.
 * Chamado de dentro do lock de cada job, então só a instância vencedora registra (as que perdem
 * a corrida do lock nem entram aqui — evita linha "OK" fantasma por réplica).
 *
 * <p>Sem {@code @Transactional} de propósito: a linha EXECUTANDO precisa commitar já pra aparecer
 * na tela enquanto o job roda; o status final vem num segundo save.</p>
 */
@Service
public class JobExecutionRecorder {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionRecorder.class);

    private final JobExecutionRepository repository;

    public JobExecutionRecorder(JobExecutionRepository repository) {
        this.repository = repository;
    }

    public void record(String jobKey, Runnable work) {
        JobExecution exec = new JobExecution();
        exec.setJobKey(jobKey);
        exec.setStatus(Constants.JOB_STATUS_RUNNING);
        exec.setStartedAt(OffsetDateTime.now());
        exec = repository.save(exec);
        try {
            work.run();
            exec.setStatus(Constants.JOB_STATUS_OK);
        } catch (RuntimeException e) {
            exec.setStatus(Constants.JOB_STATUS_ERROR);
            exec.setErrorMessage(e.getMessage());
            log.error("Job {} falhou", jobKey, e);
            throw e;
        } finally {
            OffsetDateTime end = OffsetDateTime.now();
            exec.setFinishedAt(end);
            exec.setDurationMs(Duration.between(exec.getStartedAt(), end).toMillis());
            repository.save(exec);
        }
    }
}
