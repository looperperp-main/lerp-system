package com.l.erp.billingservice.repository;

import com.l.erp.billingservice.domain.JobExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JobExecutionRepository extends JpaRepository<JobExecution, UUID> {

    /** Última execução de um job (para a tela mostrar status/duração mais recentes). */
    Optional<JobExecution> findFirstByJobKeyOrderByStartedAtDesc(String jobKey);

    /** Última execução bem-sucedida — alimenta o gauge {@code job_segundos_desde_ok}. */
    Optional<JobExecution> findFirstByJobKeyAndStatusOrderByFinishedAtDesc(String jobKey, String status);
}
