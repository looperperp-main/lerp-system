package com.l.erp.authservice.api.controllers;

import com.l.erp.authservice.api.dto.TenantDTO;
import com.l.erp.authservice.infra.config.Roles;
import com.l.erp.authservice.services.TenantService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/auth")
public class TenantController {

    private final Logger log = LoggerFactory.getLogger(TenantController.class);

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping("/tenants")
    @Secured(Roles.APP_OWNER)
    public ResponseEntity<TenantDTO> createTenant(@Valid @RequestBody TenantDTO tenantDTO) {
        log.debug("REST request to create the tenant: {}",tenantDTO);
        return ResponseEntity.ok(tenantService.createTenant(tenantDTO));
    }

    @GetMapping("/tenants")
    @Secured(Roles.APP_OWNER)
    public ResponseEntity<List<TenantDTO>> getTenants() {
        log.debug("REST request to get the list tenant");
        return ResponseEntity.ok(tenantService.getAllTenants());
    }

    @GetMapping("/tenants/{tenantId}")
    @Secured(Roles.APP_OWNER)
    public void getTenantById() {
        log.debug("REST request to get the given tenant");
    }

    @PutMapping("/tenants/{tenantId}")
    @Secured(Roles.APP_OWNER)
    public void updateTenantById() {
        log.debug("REST request to update the given tenant");
    }

    @PatchMapping("/tenants/{tenantId}/status")
    @Secured(Roles.APP_OWNER)
    public void updateTenantStatusById() {
        log.debug("REST request to update the status of the given tenant");
    }
}
