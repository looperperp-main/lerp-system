package com.l.erp.authservice.repositorios;

import com.l.erp.authservice.dominio.JobExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JobExecutionRepository extends JpaRepository<JobExecution, UUID> {

    /** Última execução de um job (para a tela mostrar status/duração mais recentes). */
    Optional<JobExecution> findFirstByJobKeyOrderByStartedAtDesc(String jobKey);
}
