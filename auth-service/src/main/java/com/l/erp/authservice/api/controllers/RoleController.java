package com.l.erp.authservice.api.controllers;

import com.l.erp.authservice.api.dto.PermissionDTO;
import com.l.erp.authservice.api.dto.RoleDTO;
import com.l.erp.authservice.api.dto.RolePermissionRequestDTO;
import com.l.erp.authservice.infra.config.Roles;
import com.l.erp.authservice.services.RolesService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

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
    public ResponseEntity<RoleDTO> createRole(@Valid @RequestBody RoleDTO roleDTO){
        log.debug("REST request to create a role: {}",roleDTO.name());
        RoleDTO created = roleService.createRole(roleDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("")
    @Secured({Roles.APP_OWNER,Roles.TENANT_OWNER})
    public ResponseEntity<List<RoleDTO>> getAllRoles(){
        log.debug("REST request to get all roles");
        List<RoleDTO> roles = roleService.getAllRoles();
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/pages")
    @Secured({Roles.APP_OWNER,Roles.TENANT_OWNER})
    public ResponseEntity<Page<RoleDTO>> getAllRolesPages(@PageableDefault(size = 10, sort = "name") Pageable pageable){
        log.debug("REST request to get all roles by page");
        Page<RoleDTO> roles = roleService.getAllRoles(pageable);
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

    @GetMapping("/{roleId}/permissions")
    @Secured({Roles.APP_OWNER, Roles.TENANT_OWNER})
    public ResponseEntity<List<PermissionDTO>> getRolePermissions(@PathVariable UUID roleId) {
        log.debug("REST request to get permissions for role: {}", roleId);
        return ResponseEntity.ok(roleService.getPermissionsByRole(roleId));
    }

    @PostMapping("/{roleId}/permissions")
    @Secured({Roles.APP_OWNER, Roles.TENANT_OWNER})
    public ResponseEntity<Void> assignPermissionsToRole(
            @PathVariable UUID roleId,
            @Valid @RequestBody RolePermissionRequestDTO request) {

        log.debug("REST request to assign permissions to role: {}", roleId);

        // Validando se o ID da URL bate com o do Body (boa prática de segurança)
        if (!roleId.equals(request.roleId())) {
            return ResponseEntity.badRequest().build();
        }

        roleService.assignPermissionsToRole(roleId, request.permissionIds());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    @Secured({Roles.APP_OWNER, Roles.TENANT_OWNER})
    public ResponseEntity<Void> removePermissionFromRole(
            @PathVariable UUID roleId,
            @PathVariable UUID permissionId) {

        log.debug("REST request to remove permission {} from role {}", permissionId, roleId);
        roleService.removePermissionFromRole(roleId, permissionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/delete")
    @Secured({Roles.APP_OWNER, Roles.TENANT_OWNER})
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
        log.debug("REST request to delete role: {}", id);
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }
}
