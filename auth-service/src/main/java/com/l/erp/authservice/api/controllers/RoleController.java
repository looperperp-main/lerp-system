package com.l.erp.authservice.api.controllers;

import com.l.erp.authservice.api.dto.RoleDTO;
import com.l.erp.authservice.infra.config.Roles;
import com.l.erp.authservice.services.RolesService;
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

import java.util.List;

@RestController
@RequestMapping("/auth/roles")
public class RoleController {

    private final Logger log = LoggerFactory.getLogger(RoleController.class);
    private final RolesService roleService;

    public RoleController(RolesService roleService) {
        this.roleService = roleService;
    }

    @PostMapping("")
    @Secured({Roles.APP_OWNER,Roles.TENANT_OWNER})
    public ResponseEntity<String> createRole(){
        log.debug("REST request to create a role");
        return ResponseEntity.ok().build();
    }

    @GetMapping("")
    @Secured({Roles.APP_OWNER,Roles.TENANT_OWNER})
    public ResponseEntity<List<RoleDTO>> getAllRoles(){
        log.debug("REST request to get all roles");
        List<RoleDTO> roles = roleService.getAllRoles();
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/{Id}")
    @Secured({Roles.APP_OWNER,Roles.TENANT_OWNER})
    public ResponseEntity<String> getRoleById(){
        log.debug("REST request to get a role by id");
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{Id}")
    @Secured({Roles.APP_OWNER,Roles.TENANT_OWNER})
    public ResponseEntity<String> updateRoleById(){
        log.debug("REST request to update a role by id");
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{Id}")
    @Secured({Roles.APP_OWNER,Roles.TENANT_OWNER})
    public ResponseEntity<String> deleteRoleById(){
        log.debug("REST request to delete a role by id");
        return ResponseEntity.ok().build();
    }
}
