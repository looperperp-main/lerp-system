package com.l.erp.authservice.api.controllers;

import com.l.erp.authservice.api.dto.RoleDTO;
import com.l.erp.authservice.api.dto.UserRoleRequestDTO;
import com.l.erp.authservice.infra.config.Roles;
import com.l.erp.authservice.services.AttributionsService;
import com.l.erp.authservice.services.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/auth/users")
public class AttributionsController {

    private final Logger log = LoggerFactory.getLogger(AttributionsController.class);

    private final AttributionsService attributionsService;

    public AttributionsController(AttributionsService attributionsService) {
        this.attributionsService = attributionsService;
    }

    @GetMapping("/{userId}/roles")
    @Secured({Roles.APP_OWNER, Roles.TENANT_OWNER})
    public ResponseEntity<List<RoleDTO>> getUserRoles(@PathVariable UUID userId){
        log.debug("REST request to get roles for user: {}", userId);
        return ResponseEntity.ok(attributionsService.getRolesByUser(userId));
    }

    @PostMapping("/{userId}/roles")
    @Secured({Roles.APP_OWNER, Roles.TENANT_OWNER})
    public ResponseEntity<Void> assignRolesToUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UserRoleRequestDTO request){

        log.debug("REST request to add roles to a user: {}", userId);

        if (!userId.equals(request.userId())) {
            return ResponseEntity.badRequest().build();
        }

        attributionsService.assignRolesToUser(userId, request.roleIds());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{userId}/roles/{roleId}")
    @Secured({Roles.APP_OWNER, Roles.TENANT_OWNER})
    public ResponseEntity<Void> removeRoleFromUser(
            @PathVariable UUID userId,
            @PathVariable UUID roleId){

        log.debug("REST request to remove a role from a user");
        attributionsService.removeRoleFromUser(userId, roleId);
        return ResponseEntity.noContent().build();
    }
}