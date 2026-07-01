package com.l.erp.authservice.api.controllers;

import com.l.erp.authservice.api.dto.PermissionDTO;
import com.l.erp.authservice.api.dto.RoleDTO;
import com.l.erp.authservice.api.dto.RolePermissionRequestDTO;
import com.l.erp.authservice.api.dto.lists.RoleSearchFilterDTO;
import com.l.erp.authservice.services.RolesService;
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
    @PreAuthorize("hasAuthority('ROLE_INSERT')")
    public ResponseEntity<RoleDTO> createRole(@Valid @RequestBody RoleDTO roleDTO){
        log.debug("REST request to create a role: {}",roleDTO.name());
        RoleDTO created = roleService.createRole(roleDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public ResponseEntity<List<RoleDTO>> getAllRoles(){
        log.debug("REST request to get all roles");
        List<RoleDTO> roles = roleService.getAllRoles();
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/pages")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public ResponseEntity<Page<RoleDTO>> getAllRolesPages(@PageableDefault(size = 10, sort = "name") Pageable pageable){
        log.debug("REST request to get all roles by page");
        Page<RoleDTO> roles = roleService.getAllRoles(pageable);
        return ResponseEntity.ok(roles);
    }

    @PostMapping("/search")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public ResponseEntity<Page<RoleDTO>> searchRoles(
            @RequestBody RoleSearchFilterDTO filter,
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        log.debug("REST request to search roles by page and filters");
        Page<RoleDTO> roles = roleService.searchRoles(filter, pageable);
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/{Id}")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public ResponseEntity<String> getRoleById(@PathVariable UUID Id){
        log.debug("REST request to get a role by id {}", Id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{Id}")
    @PreAuthorize("hasAuthority('ROLE_UPDATE')")
    public ResponseEntity<String> updateRoleById(@PathVariable UUID Id){
        log.debug("REST request to update a role by id {}", Id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{Id}")
    @PreAuthorize("hasAuthority('ROLE_DELETE')")
    public ResponseEntity<String> deleteRoleById(@PathVariable UUID Id){
        log.debug("REST request to delete a role by id {}", Id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('PERMISSION_READ')")
    public ResponseEntity<List<PermissionDTO>> getRolePermissions(@PathVariable UUID roleId) {
        log.debug("REST request to get permissions for role: {}", roleId);
        return ResponseEntity.ok(roleService.getPermissionsByRole(roleId));
    }

    @PostMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('PERMISSION_UPDATE')")
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
    @PreAuthorize("hasAuthority('PERMISSION_UPDATE')")
    public ResponseEntity<Void> removePermissionFromRole(
            @PathVariable UUID roleId,
            @PathVariable UUID permissionId) {

        log.debug("REST request to remove permission {} from role {}", permissionId, roleId);
        roleService.removePermissionFromRole(roleId, permissionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('ROLE_DELETE')")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
        log.debug("REST request to delete role: {}", id);
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }
}
