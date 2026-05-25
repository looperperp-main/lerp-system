package com.l.erp.authservice.api.mappers;

import com.l.erp.authservice.api.dto.audit.AuditLogDTO;
import com.l.erp.authservice.dominio.audit.AuditLog;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AuditMapper {

    AuditLogDTO toAuditLogDTO(AuditLog auditLog);

    AuditLog toAuditLog(AuditLogDTO auditLogDTO);

    List<AuditLogDTO> toAuditLogDTOs(List<AuditLog> auditLogs);
}
