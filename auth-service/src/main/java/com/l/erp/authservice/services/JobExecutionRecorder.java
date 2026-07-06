package com.l.erp.authservice.services;

import com.l.erp.authservice.dominio.JobExecution;
import com.l.erp.authservice.repositorios.JobExecutionRepository;
import com.l.erp.common.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Grava início/fim/status/duração de uma execução de job em {@code auth.job_execution}.
 * Chamado de dentro de cada scheduler de trial — captura o disparo manual (admin) e o cron.
 *
 * <p>Sem {@code @Transactional} de propósito: fora de transação (disparo manual, D+10), cada save
 * commita sozinho, então a linha EXECUTANDO aparece já. <b>Ressalva:</b> quando o chamador é
 * {@code @Transactional} (D+15), os saves entram nessa transação — se ela der rollback total, o
 * registro some junto. Aceitável: os erros por-tenant já são capturados no loop, então o método
 * D+15 raramente estoura inteiro. ponytail: se precisar registrar rollback, mover para REQUIRES_NEW.</p>
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
