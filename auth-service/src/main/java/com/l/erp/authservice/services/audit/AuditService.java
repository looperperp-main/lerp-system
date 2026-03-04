package com.l.erp.authservice.services.audit;

import com.l.erp.authservice.api.dto.CurrentUser;
import com.l.erp.authservice.api.dto.audit.AuditLogDTO;
import com.l.erp.authservice.api.mappers.AuditMapper;
import com.l.erp.authservice.dominio.audit.AuditLog;
import com.l.erp.authservice.repositorios.audit.AuditRepository;
import com.l.erp.authservice.util.SecurityUtils;
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
     * @param action ação
     * @param targetType Tipo do Alvo
     * @param targetId Id do Alvo
     * @param result resultado
     * @param detailsJson destlhes em Json se necessário
     * @param correlationId correlation ID
     */
    public void logAuditEvent(String action, String targetType, UUID targetId, String result, String detailsJson, UUID correlationId) {
        CurrentUser currentUser = SecurityUtils.getCurrentUserInfo();

        AuditLog auditLog = new AuditLog();
        auditLog.setAction(action);
        auditLog.setActorUserId(currentUser.id());
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
     * @param pageable paginavel
     * @return pagina
     */
    public Page<AuditLogDTO> getAuditLogs(Pageable pageable) {
        return auditRepository.findAll(pageable).map(auditMapper::toAuditLogDTO);
    }
}
