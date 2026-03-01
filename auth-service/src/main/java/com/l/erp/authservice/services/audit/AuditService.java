package com.l.erp.authservice.services.audit;

import com.l.erp.authservice.api.dto.audit.AuditLogDTO;
import com.l.erp.authservice.api.mappers.AuditMapper;
import com.l.erp.authservice.dominio.audit.AuditLog;
import com.l.erp.authservice.repositorios.audit.AuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuditService {

    private final Logger logger = LoggerFactory.getLogger(AuditService.class);

    private final AuditRepository auditRepository;

    private final AuditMapper auditMapper;

    public AuditService(
            AuditRepository auditRepository,
            AuditMapper auditMapper
    ) {
        this.auditRepository = auditRepository;
        this.auditMapper = auditMapper;
    }

    /**
     * Registra um evento de auditoria
     * @param action
     * @param userId
     * @param targetType
     * @param targetId
     * @param result
     * @param detailsJson
     * @param correlationId
     */
    public void logAuditEvent(String action, UUID userId, String targetType, UUID targetId, String result, String detailsJson, UUID correlationId) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(action);
        auditLog.setActorUserId(userId);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setResult(result);
        auditLog.setDetailsJson(Optional.ofNullable(detailsJson).orElse("{}"));
        auditLog.setCorrelationId(correlationId);
        auditLog.setEventDate(Instant.now());
        auditRepository.save(auditLog);
        logger.info("Audit event logged: {}", auditLog);
    }

    /**
     * Retorna todos os logs de auditoria
     * @param pageable
     * @return
     */
    public Page<AuditLogDTO> getAuditLogs(Pageable pageable) {
        return auditRepository.findAll(pageable).map(auditMapper::toAuditLogDTO);
    }
}
