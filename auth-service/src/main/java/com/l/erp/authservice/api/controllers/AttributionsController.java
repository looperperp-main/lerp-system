package com.l.erp.authservice.api.controllers;

import com.l.erp.authservice.infra.config.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/users")
public class AttributionsController {

    private final Logger log = LoggerFactory.getLogger(AttributionsController.class);

    public AttributionsController() {
    }

    @PostMapping("{id}/roles")
    @Secured({Roles.APP_OWNER, Roles.TENANT_OWNER})
    public ResponseEntity<String> addRoleToUser(){
        log.debug("REST request to add a role to a user");
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("{id}/roles/{roleId}")
    @Secured({Roles.APP_OWNER, Roles.TENANT_OWNER})
    public ResponseEntity<String> removeRoleFromUser(){
        log.debug("REST request to remove a role from a user");
        return ResponseEntity.ok().build();
    }
}