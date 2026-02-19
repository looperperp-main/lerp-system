package com.l.erp.authservice.repositorios.audit;

import com.l.erp.authservice.dominio.audit.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditLog, Long> {
}
