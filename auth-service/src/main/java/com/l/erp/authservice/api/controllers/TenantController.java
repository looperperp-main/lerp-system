package com.l.erp.authservice.api.controllers;

import com.l.erp.authservice.api.dto.TenantDTO;
import com.l.erp.authservice.infra.config.Roles;
import com.l.erp.authservice.services.TenantService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class TenantController {

    private final Logger log = LoggerFactory.getLogger(TenantController.class);

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping("/tenants")
    @PreAuthorize("hasAuthority('TENANT_INSERT')")
    public ResponseEntity<TenantDTO> createTenant(@Valid @RequestBody TenantDTO tenantDTO) {
        log.debug("REST request to create the tenant: ");
        TenantDTO createdTenant = tenantService.createTenant(tenantDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTenant);
    }

    @GetMapping("/tenants")
    @PreAuthorize("hasAuthority('TENANT_READ')")
    public ResponseEntity<Page<TenantDTO>> getTenants(@PageableDefault(size = 10, sort = "name") Pageable pageable) {
        log.debug("REST request to get the list tenant");
        return ResponseEntity.ok(tenantService.getAllTenants(pageable));
    }

    @GetMapping("/tenants/active")
    @PreAuthorize("hasAuthority('TENANT_READ')")
    public ResponseEntity<Page<TenantDTO>> getTenantsActive(@PageableDefault(size = 10, sort = "name") Pageable pageable) {
        log.debug("REST request to get the list of Active Tenants tenant");
        return ResponseEntity.ok(tenantService.getAllActiveTenants(pageable));
    }

    @GetMapping("/tenants/{tenantId}")
    @PreAuthorize("hasAuthority('TENANT_READ')")
    public ResponseEntity<TenantDTO> getTenantById(@Valid @PathVariable Long tenantId) {
        log.debug("REST request to get the given tenant");
        return ResponseEntity.ok(tenantService.getTenantById(tenantId));
    }

    @PutMapping("/tenants")
    @PreAuthorize("hasAuthority('TENANT_UPDATE')")
    public ResponseEntity<TenantDTO> updateTenant(@Valid @RequestBody TenantDTO tenantDTO) {
        log.debug("REST request to update the given tenant");
        return ResponseEntity.ok(tenantService.updateTenant(tenantDTO));
    }

    @PatchMapping("/tenants/{tenantId}/status")
    @PreAuthorize("hasAuthority('TENANT_DELETE')")
    public ResponseEntity<Void> updateTenantStatusById(@Valid @PathVariable Long tenantId, @Valid @RequestBody String status) {
        log.debug("REST request to update the status of the given tenant");
        tenantService.updateTenantStatusById(tenantId,status);
        return ResponseEntity.noContent().build();
    }
}
