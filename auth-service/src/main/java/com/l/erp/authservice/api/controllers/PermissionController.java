package com.l.erp.authservice.api.controllers;

import com.l.erp.authservice.infra.config.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/permissions")
public class PermissionController {

    private final Logger log = LoggerFactory.getLogger(PermissionController.class);

    public PermissionController() {

    }

    @PostMapping("")
    @Secured({Roles.APP_OWNER, Roles.TENANT_OWNER})
    public ResponseEntity<String> createPermission(){
        log.debug("REST request to create a permission");
        return ResponseEntity.ok().build();
    }

    @GetMapping("")
    @Secured({Roles.APP_OWNER, Roles.TENANT_OWNER})
    public ResponseEntity<String> getAllPermissions(){
        log.debug("REST request to get all permissions");
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{permissionId}")
    @Secured({Roles.APP_OWNER, Roles.TENANT_OWNER})
    public ResponseEntity<String> updatePermissionById(){
        log.debug("REST request to update a permission by id");
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{permissionId}")
    @Secured({Roles.APP_OWNER, Roles.TENANT_OWNER})
    public ResponseEntity<String> deletePermissionById(){
        log.debug("REST request to delete a permission by id");
        return ResponseEntity.ok().build();
    }
}
