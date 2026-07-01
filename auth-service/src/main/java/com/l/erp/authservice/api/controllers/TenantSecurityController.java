package com.l.erp.authservice.api.controllers;

import com.l.erp.authservice.api.dto.PermissionDTO;
import com.l.erp.authservice.api.dto.RoleDTO;
import com.l.erp.authservice.api.dto.RolePermissionRequestDTO;
import com.l.erp.authservice.api.dto.UserAccountDTO;
import com.l.erp.authservice.api.dto.UserAccountPageDTO;
import com.l.erp.authservice.api.dto.UserRoleRequestDTO;
import com.l.erp.authservice.api.dto.lists.RoleSearchFilterDTO;
import com.l.erp.authservice.api.dto.lists.UserSearchFilterDTO;
import com.l.erp.authservice.services.AttributionsService;
import com.l.erp.authservice.services.PermissionService;
import com.l.erp.authservice.services.RolesService;
import com.l.erp.authservice.services.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints de segurança (roles/permissões/usuários) para o <b>portal do tenant</b>.
 *
 * <p>Diferente de {@code /auth/{roles,users,permissions}} (admin da plataforma, multi-tenant),
 * aqui o tenant é sempre derivado do header {@code X-Tenant-Id} injetado pelo gateway — nunca do
 * body. Toda leitura/escrita é tenant-scoped (IDOR-safe), e a listagem de permissões só expõe
 * escopo {@code TENANT}.</p>
 *
 * <p>Autorização por <b>permissão</b> (claim {@code authorities} do JWT do tenant), não por role —
 * cada rota exige a permissão granular do recurso (família USER_, ROLE_ ou PERMISSION_).</p>
 */
@RestController
@RequestMapping("/auth/tenant/security")
public class TenantSecurityController {

    private final RolesService rolesService;
    private final PermissionService permissionService;
    private final UserService userService;
    private final AttributionsService attributionsService;

    public TenantSecurityController(RolesService rolesService,
                                    PermissionService permissionService,
                                    UserService userService,
                                    AttributionsService attributionsService) {
        this.rolesService = rolesService;
        this.permissionService = permissionService;
        this.userService = userService;
        this.attributionsService = attributionsService;
    }

    // ------------------------------------------------------------------ Roles

    @PostMapping("/roles/search")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public ResponseEntity<Page<RoleDTO>> searchRoles(@RequestHeader("X-Tenant-Id") Long tenantId,
                                                     @RequestBody RoleSearchFilterDTO filter,
                                                     @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(rolesService.searchRolesByTenant(filter, pageable, tenantId));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public ResponseEntity<List<RoleDTO>> listRoles(@RequestHeader("X-Tenant-Id") Long tenantId) {
        return ResponseEntity.ok(rolesService.getRolesByTenant(tenantId));
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_INSERT')")
    public ResponseEntity<RoleDTO> createRole(@RequestHeader("X-Tenant-Id") Long tenantId,
                                              @Valid @RequestBody RoleDTO roleDTO) {
        RoleDTO created = rolesService.createRoleForTenant(roleDTO, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('ROLE_DELETE')")
    public ResponseEntity<Void> deleteRole(@RequestHeader("X-Tenant-Id") Long tenantId,
                                           @PathVariable UUID roleId) {
        rolesService.deleteRoleForTenant(roleId, tenantId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('PERMISSION_READ')")
    public ResponseEntity<List<PermissionDTO>> getRolePermissions(@RequestHeader("X-Tenant-Id") Long tenantId,
                                                                  @PathVariable UUID roleId) {
        return ResponseEntity.ok(rolesService.getPermissionsByRoleForTenant(roleId, tenantId));
    }

    @PostMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('PERMISSION_UPDATE')")
    public ResponseEntity<Void> assignPermissions(@RequestHeader("X-Tenant-Id") Long tenantId,
                                                  @PathVariable UUID roleId,
                                                  @Valid @RequestBody RolePermissionRequestDTO request) {
        if (!roleId.equals(request.roleId())) {
            return ResponseEntity.badRequest().build();
        }
        rolesService.assignPermissionsToRoleForTenant(roleId, request.permissionIds(), tenantId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/roles/{roleId}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('PERMISSION_UPDATE')")
    public ResponseEntity<Void> removePermission(@RequestHeader("X-Tenant-Id") Long tenantId,
                                                 @PathVariable UUID roleId,
                                                 @PathVariable UUID permissionId) {
        rolesService.removePermissionFromRoleForTenant(roleId, permissionId, tenantId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------ Permissions

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('PERMISSION_READ')")
    public ResponseEntity<Page<PermissionDTO>> listPermissions(@PageableDefault(size = 500, sort = "domain") Pageable pageable) {
        return ResponseEntity.ok(permissionService.getTenantScopedPermissions(pageable));
    }

    // ------------------------------------------------------------------ Users

    @PostMapping("/users/search")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<Page<UserAccountPageDTO>> searchUsers(@RequestHeader("X-Tenant-Id") Long tenantId,
                                                                @RequestBody UserSearchFilterDTO filter,
                                                                @PageableDefault(size = 10, sort = "displayName") Pageable pageable) {
        return ResponseEntity.ok(userService.searchAccountsForTenant(filter, pageable, tenantId));
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('USER_INSERT')")
    public ResponseEntity<UserAccountDTO> createUser(@RequestHeader("X-Tenant-Id") Long tenantId,
                                                     @Valid @RequestBody UserAccountDTO userDTO) {
        UserAccountDTO created = userService.createUserForTenant(userDTO, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<UserAccountDTO> updateUser(@RequestHeader("X-Tenant-Id") Long tenantId,
                                                     @PathVariable UUID userId,
                                                     @Valid @RequestBody UserAccountDTO userDTO) {
        return ResponseEntity.ok(userService.updateUserForTenant(userId, userDTO, tenantId));
    }

    @PatchMapping("/users/{userId}/status")
    @PreAuthorize("hasAuthority('USER_STATUS')")
    public ResponseEntity<Void> updateUserStatus(@RequestHeader("X-Tenant-Id") Long tenantId,
                                                 @PathVariable UUID userId) {
        userService.updateUserStatusForTenant(userId, tenantId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<List<RoleDTO>> getUserRoles(@RequestHeader("X-Tenant-Id") Long tenantId,
                                                      @PathVariable UUID userId) {
        return ResponseEntity.ok(attributionsService.getRolesByUserForTenant(userId, tenantId));
    }

    @PostMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<Void> assignRoles(@RequestHeader("X-Tenant-Id") Long tenantId,
                                            @PathVariable UUID userId,
                                            @Valid @RequestBody UserRoleRequestDTO request) {
        if (!userId.equals(request.userId())) {
            return ResponseEntity.badRequest().build();
        }
        attributionsService.assignRolesToUserForTenant(userId, request.roleIds(), tenantId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{userId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<Void> removeRole(@RequestHeader("X-Tenant-Id") Long tenantId,
                                           @PathVariable UUID userId,
                                           @PathVariable UUID roleId) {
        attributionsService.removeRoleFromUserForTenant(userId, roleId, tenantId);
        return ResponseEntity.noContent().build();
    }
}