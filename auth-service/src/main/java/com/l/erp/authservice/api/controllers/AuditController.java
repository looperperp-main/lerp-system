package com.l.erp.authservice.api.controllers;

import com.l.erp.authservice.api.dto.audit.AuditLogDTO;
import com.l.erp.authservice.infra.config.Roles;
import com.l.erp.authservice.services.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuditController {

    private final Logger log = LoggerFactory.getLogger(AuditController.class);

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/audits")
    @Secured(Roles.APP_OWNER)
    public ResponseEntity<Page<AuditLogDTO>> getAudits(@PageableDefault(size = 25) Pageable pageable) {
        log.debug("REST request to get all audits");
        return ResponseEntity.ok(auditService.getAuditLogs(pageable));
    }
}
