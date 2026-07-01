package com.l.erp.authservice.api.controllers;

import com.l.erp.authservice.api.dto.PermissionDTO;
import com.l.erp.authservice.services.PermissionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/auth/permissions")
public class PermissionController {

    private final Logger log = LoggerFactory.getLogger(PermissionController.class);
    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @PostMapping("")
    @PreAuthorize("hasAuthority('PERMISSION_INSERT')")
    public ResponseEntity<PermissionDTO> createPermission(@Valid @RequestBody PermissionDTO permissionDTO){
        log.debug("REST request to create a permission: {}", permissionDTO.code());
        PermissionDTO created = permissionService.createPermission(permissionDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("")
    @PreAuthorize("hasAuthority('PERMISSION_READ')")
    public ResponseEntity<Page<PermissionDTO>> getAllPermissions(@PageableDefault(size = 10, sort = "domain") Pageable pageable){
        log.debug("REST request to get all permissions");
        return ResponseEntity.ok(permissionService.getAllPermissions(pageable));
    }

    @GetMapping("/{permissionId}")
    @PreAuthorize("hasAuthority('PERMISSION_READ')")
    public ResponseEntity<PermissionDTO> getPermissionById(@PathVariable UUID permissionId){
        log.debug("REST request to get a permission by id: {}", permissionId);
        return ResponseEntity.ok(permissionService.getPermissionById(permissionId));
    }

    @PutMapping("/{permissionId}")
    @PreAuthorize("hasAuthority('PERMISSION_UPDATE')")
    public ResponseEntity<PermissionDTO> updatePermissionById(@PathVariable UUID permissionId, @Valid @RequestBody PermissionDTO permissionDTO){
        log.debug("REST request to update a permission by id: {}", permissionId);
        return ResponseEntity.ok(permissionService.updatePermission(permissionId, permissionDTO));
    }

    @DeleteMapping("/{permissionId}")
    @PreAuthorize("hasAuthority('PERMISSION_DELETE')")
    public ResponseEntity<Void> deletePermissionById(@PathVariable UUID permissionId){
        log.debug("REST request to delete a permission by id: {}", permissionId);
        permissionService.deletePermission(permissionId);
        return ResponseEntity.noContent().build();
    }
}
