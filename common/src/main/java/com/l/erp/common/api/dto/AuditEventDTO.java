package com.l.erp.common.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEventDTO(
        String action,          // ex: "CREATE_GRUPO_CLIENTE"
        UUID actorId,           // userId
        String targetType,       // "USER" ou "SYSTEM"
        UUID targetId,          // Id do recurso afetado (ex: o ID do Grupo de Cliente)
        String result,          // "SUCCESS" ou "FAILED"
        String detailsJson,         // Detalhes extras, JSON, ou null
        UUID correlationId,         // Inquilino
        Instant timestamp
) {
}
