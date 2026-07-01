package com.l.erp.authservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.authservice.api.controllers.TenantSecurityController;
import com.l.erp.authservice.api.dto.PermissionDTO;
import com.l.erp.authservice.api.dto.RoleDTO;
import com.l.erp.authservice.api.dto.RolePermissionRequestDTO;
import com.l.erp.authservice.api.dto.UserRoleRequestDTO;
import com.l.erp.authservice.configuration.ObjectMapperConfig;
import com.l.erp.authservice.services.AttributionsService;
import com.l.erp.authservice.services.PermissionService;
import com.l.erp.authservice.services.RolesService;
import com.l.erp.authservice.services.UserService;
import com.l.erp.common.exception.custom.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Garante o contrato do portal do tenant: tenant vem do header X-Tenant-Id, mismatch de id → 400,
 * bloqueio de permissão PLATFORM propagado como 403, e que o tenantId do header chega no service.
 */
@WebMvcTest(controllers = TenantSecurityController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ObjectMapperConfig.class)
class TenantSecurityControllerTest {

    private static final long TENANT = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RolesService rolesService;
    @MockitoBean
    private PermissionService permissionService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private AttributionsService attributionsService;

    @Test
    @WithMockUser(authorities = "ROLE_READ")
    void searchRolesPassesTenantFromHeader() throws Exception {
        RoleDTO role = new RoleDTO(UUID.randomUUID(), "GESTOR", TENANT, null, null, null, null);
        when(rolesService.searchRolesByTenant(any(), any(), eq(TENANT)))
                .thenReturn(new PageImpl<>(List.of(role)));

        mockMvc.perform(post("/auth/tenant/security/roles/search")
                        .header("X-Tenant-Id", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("GESTOR"));

        verify(rolesService).searchRolesByTenant(any(), any(), eq(TENANT));
    }

    @Test
    @WithMockUser(authorities = "PERMISSION_READ")
    void listTenantScopedPermissions() throws Exception {
        PermissionDTO perm = new PermissionDTO(UUID.randomUUID(), "CLIENTE_LER", "CADASTRO",
                "TENANT", "Ler clientes", null, null, null, null);
        when(permissionService.getTenantScopedPermissions(any()))
                .thenReturn(new PageImpl<>(List.of(perm)));

        mockMvc.perform(get("/auth/tenant/security/permissions").header("X-Tenant-Id", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("CLIENTE_LER"))
                .andExpect(jsonPath("$.content[0].scope").value("TENANT"));
    }

    @Test
    @WithMockUser(authorities = "PERMISSION_UPDATE")
    void assignPermissionsRejectsIdMismatch() throws Exception {
        UUID roleId = UUID.randomUUID();
        RolePermissionRequestDTO req = new RolePermissionRequestDTO(UUID.randomUUID(), List.of(UUID.randomUUID()));

        mockMvc.perform(post("/auth/tenant/security/roles/{roleId}/permissions", roleId)
                        .header("X-Tenant-Id", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "PERMISSION_UPDATE")
    void assignPlatformPermissionIsForbidden() throws Exception {
        UUID roleId = UUID.randomUUID();
        UUID permId = UUID.randomUUID();
        RolePermissionRequestDTO req = new RolePermissionRequestDTO(roleId, List.of(permId));

        doThrow(new BusinessException("Permissão de escopo PLATFORM não pode ser atribuída neste portal", HttpStatus.FORBIDDEN))
                .when(rolesService).assignPermissionsToRoleForTenant(eq(roleId), any(), eq(TENANT));

        mockMvc.perform(post("/auth/tenant/security/roles/{roleId}/permissions", roleId)
                        .header("X-Tenant-Id", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "USER_UPDATE")
    void assignRolesRejectsIdMismatch() throws Exception {
        UUID userId = UUID.randomUUID();
        UserRoleRequestDTO req = new UserRoleRequestDTO(UUID.randomUUID(), List.of(UUID.randomUUID()));

        mockMvc.perform(post("/auth/tenant/security/users/{userId}/roles", userId)
                        .header("X-Tenant-Id", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "USER_STATUS")
    void updateUserStatusPassesTenant() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(patch("/auth/tenant/security/users/{userId}/status", userId)
                        .header("X-Tenant-Id", TENANT))
                .andExpect(status().isNoContent());

        verify(userService).updateUserStatusForTenant(eq(userId), eq(TENANT));
    }
}