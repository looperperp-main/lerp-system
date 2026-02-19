package com.l.erp.authservice.services.audit;

import com.l.erp.authservice.dominio.audit.AuditLog;
import com.l.erp.authservice.repositorios.audit.AuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuditService {

    private final Logger logger = LoggerFactory.getLogger(AuditService.class);

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void logAuditEvent(String action, UUID userId, String targetType, UUID targetId, String result, String detailsJson, UUID correlationId) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(action);
        auditLog.setActorUserId(userId);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setResult(result);
        auditLog.setDetailsJson(Optional.ofNullable(detailsJson).orElse("{}"));
        auditLog.setCorrelationId(correlationId);
        auditRepository.save(auditLog);
        logger.info("Audit event logged: {}", auditLog);
    }
}
