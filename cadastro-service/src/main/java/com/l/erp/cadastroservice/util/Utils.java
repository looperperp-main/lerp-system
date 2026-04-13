package com.l.erp.cadastroservice.util;

import com.l.erp.cadastroservice.services.AuditProducerService;
import com.l.erp.common.api.dto.AuditEventDTO;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class Utils {

    private final AuditProducerService auditProducer;

    public Utils(AuditProducerService auditProducer) {
        this.auditProducer = auditProducer;
    }

    /**
     * Sends an audit event to the audit producer, containing information about an action performed in the system.
     *
     * @param actorId       The unique identifier of the actor performing the action.
     * @param targetId      The unique identifier of the target entity involved in the action (can be null).
     * @param result        The result of the action, typically represented as a string (e.g., "SUCCESS", "ERROR").
     * @param detailsJson   A JSON string containing additional details about the event (can be null).
     * @param correlationId A unique identifier used to correlate logs and events for tracing purposes.
     */
    public void sendAuditEvent(String action, UUID actorId, String targetType, UUID targetId, String result, String detailsJson, UUID correlationId) {
        AuditEventDTO auditEvent;
        auditEvent = new AuditEventDTO(

                action,
                actorId,
                targetType,
                targetId,
                result,
                detailsJson,
                correlationId,
                Instant.now()
        );

        auditProducer.sendAuditEvent(auditEvent);
    }
}
