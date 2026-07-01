package com.l.erp.authservice.repositorios.audit;

import com.l.erp.authservice.dominio.audit.ConsumerErrorLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConsumerErrorLogRepository extends JpaRepository<ConsumerErrorLog, UUID> {
}