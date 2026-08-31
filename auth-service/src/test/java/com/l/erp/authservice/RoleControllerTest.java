package com.l.erp.authservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.authservice.api.controllers.RoleController;
import com.l.erp.authservice.api.dto.CurrentUser;
import com.l.erp.authservice.api.dto.RoleDTO;
import com.l.erp.authservice.api.dto.RolePermissionRequestDTO;
import com.l.erp.authservice.api.dto.lists.RoleSearchFilterDTO;
import com.l.erp.authservice.api.mappers.RoleMapper;
import com.l.erp.authservice.configuration.ObjectMapperConfig;
import com.l.erp.authservice.dominio.Permission;
import com.l.erp.authservice.dominio.Role;
import com.l.erp.authservice.dominio.RolePermission;
import com.l.erp.authservice.dominio.RolePermissionId;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.repositorios.PermissionRepository;
import com.l.erp.authservice.repositorios.RolePermissionRepository;
import com.l.erp.authservice.repositorios.RoleRepository;
import com.l.erp.authservice.repositorios.TenantRepository;
import com.l.erp.authservice.repositorios.UserRoleRepository;
import com.l.erp.authservice.services.RolesService;
import com.l.erp.authservice.services.audit.AuditService;
import com.l.erp.authservice.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RoleController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RolesService.class, ObjectMapperConfig.class})
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private PermissionRepository permissionRepository;

    @MockitoBean
    private RolePermissionRepository rolePermissionRepository;

    @MockitoBean
    private TenantRepository tenantRepository;

    @MockitoBean
    private RoleMapper roleMapper;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private UserRoleRepository userRoleRepository;

    @Test
    @WithMockUser(authorities = {"ROLE_READ", "ROLE_INSERT", "ROLE_UPDATE", "ROLE_DELETE", "PERMISSION_READ", "PERMISSION_UPDATE"})
    void shouldDenyAccessWithoutAppOwnerRole() throws Exception {
        mockMvc.perform(get("/auth/roles"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_READ", "ROLE_INSERT", "ROLE_UPDATE", "ROLE_DELETE", "PERMISSION_READ", "PERMISSION_UPDATE", "ROLE_APP_OWNER"})
    void shouldGetAllRoles() throws Exception {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName("ADMIN");

        RoleDTO dto = new RoleDTO(role.getId(), "ADMIN", 1L, Instant.now(), "seed", null, null, null);

        when(roleRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(role)));
        when(roleMapper.toRoleDTOs(any())).thenReturn(List.of(dto));

        mockMvc.perform(get("/auth/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("ADMIN"));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_READ", "ROLE_INSERT", "ROLE_UPDATE", "ROLE_DELETE", "PERMISSION_READ", "PERMISSION_UPDATE", "ROLE_APP_OWNER"})
    void shouldGetAllRolesPaged() throws Exception {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName("VIEWER");

        RoleDTO dto = new RoleDTO(role.getId(), "VIEWER", 1L, Instant.now(), "seed", null, null, null);

        Page<Role> page = new PageImpl<>(List.of(role));
        when(roleRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(roleMapper.toRoleDTO(any(Role.class))).thenReturn(dto);

        mockMvc.perform(get("/auth/roles/pages?page=0&size=10"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_READ", "ROLE_INSERT", "ROLE_UPDATE", "ROLE_DELETE", "PERMISSION_READ", "PERMISSION_UPDATE", "ROLE_APP_OWNER"})
    void shouldSearchRoles() throws Exception {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName("EDITOR");

        RoleDTO dto = new RoleDTO(role.getId(), "EDITOR", 1L, Instant.now(), "seed", null, null, null);

        Page<Role> page = new PageImpl<>(List.of(role));
        when(roleRepository.findWithFilters(any(), any(Pageable.class))).thenReturn(page);
        when(roleMapper.toRoleDTO(any(Role.class))).thenReturn(dto);

        RoleSearchFilterDTO filter = new RoleSearchFilterDTO("EDITOR", null);

        mockMvc.perform(post("/auth/roles/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(filter)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_READ", "ROLE_INSERT", "ROLE_UPDATE", "ROLE_DELETE", "PERMISSION_READ", "PERMISSION_UPDATE", "ROLE_APP_OWNER"})
    void shouldCreateRole() throws Exception {
        UUID roleId = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(1L);

        Role saved = new Role();
        saved.setId(roleId);
        saved.setName("GESTOR");
        saved.setTenant(tenant);
        saved.setCreatedBy("test@test.com");
        saved.setCreatedDate(Instant.now());

        RoleDTO input = new RoleDTO(null, "GESTOR", 1L, null, null, null, null, "Responsável pela gestão do time");
        RoleDTO output = new RoleDTO(roleId, "GESTOR", 1L, Instant.now(), "test@test.com", null, null, "Responsável pela gestão do time");

        when(roleRepository.findByNameAndTenant_Id("GESTOR", 1L)).thenReturn(Optional.empty());
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(roleRepository.save(any(Role.class))).thenReturn(saved);
        when(roleMapper.toRoleDTO(saved)).thenReturn(output);

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));
            utils.when(() -> SecurityUtils.getCorrelationIdFromRequest(any()))
                    .thenReturn(UUID.randomUUID());

            mockMvc.perform(post("/auth/roles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(input)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("GESTOR"))
                    .andExpect(jsonPath("$.descricao").value("Responsável pela gestão do time"));
        }
    }

    @Test
    @WithMockUser(authorities = {"ROLE_READ", "ROLE_INSERT", "ROLE_UPDATE", "ROLE_DELETE", "PERMISSION_READ", "PERMISSION_UPDATE", "ROLE_APP_OWNER"})
    void shouldCreateRoleDuplicated() throws Exception {
        Role existing = new Role();
        existing.setId(UUID.randomUUID());
        existing.setName("GESTOR");

        RoleDTO input = new RoleDTO(null, "GESTOR", 1L, null, null, null, null, null);

        when(roleRepository.findByNameAndTenant_Id("GESTOR", 1L)).thenReturn(Optional.of(existing));

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));

            mockMvc.perform(post("/auth/roles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(input)))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Erro de Negócio"));
        }
    }

    @Test
    @WithMockUser(authorities = {"ROLE_READ", "ROLE_INSERT", "ROLE_UPDATE", "ROLE_DELETE", "PERMISSION_READ", "PERMISSION_UPDATE", "ROLE_APP_OWNER"})
    void shouldUpdateRole() throws Exception {
        UUID roleId = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(1L);

        Role role = new Role();
        role.setId(roleId);
        role.setName("GESTOR");
        role.setTenant(tenant);

        RoleDTO input = new RoleDTO(null, "GESTOR_ATUALIZADO", 1L, null, null, null, null, "Descrição atualizada");
        RoleDTO output = new RoleDTO(roleId, "GESTOR_ATUALIZADO", 1L, null, "seed", Instant.now(), "test@test.com", "Descrição atualizada");

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRepository.findByNameAndTenant_Id("GESTOR_ATUALIZADO", 1L)).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenReturn(role);
        when(roleMapper.toRoleDTO(role)).thenReturn(output);

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));
            utils.when(() -> SecurityUtils.getCorrelationIdFromRequest(any()))
                    .thenReturn(UUID.randomUUID());

            mockMvc.perform(put("/auth/roles/{Id}", roleId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(input)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("GESTOR_ATUALIZADO"));
        }
    }

    @Test
    @WithMockUser(authorities = {"ROLE_READ", "ROLE_INSERT", "ROLE_UPDATE", "ROLE_DELETE", "PERMISSION_READ", "PERMISSION_UPDATE", "ROLE_APP_OWNER"})
    void shouldUpdateRoleNotFound() throws Exception {
        UUID roleId = UUID.randomUUID();

        RoleDTO input = new RoleDTO(null, "GESTOR_ATUALIZADO", 1L, null, null, null, null, null);

        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        mockMvc.perform(put("/auth/roles/{Id}", roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Erro de Negócio"));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_READ", "ROLE_INSERT", "ROLE_UPDATE", "ROLE_DELETE", "PERMISSION_READ", "PERMISSION_UPDATE", "ROLE_APP_OWNER"})
    void shouldGetRoleById() throws Exception {
        UUID roleId = UUID.randomUUID();

        mockMvc.perform(get("/auth/roles/{Id}", roleId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_READ", "ROLE_INSERT", "ROLE_UPDATE", "ROLE_DELETE", "PERMISSION_READ", "PERMISSION_UPDATE", "ROLE_APP_OWNER"})
    void shouldGetRolePermissions() throws Exception {
        UUID roleId = UUID.randomUUID();
        UUID permId = UUID.randomUUID();

        Role role = new Role();
        role.setId(roleId);
        role.setName("ADMIN");

        Permission permission = new Permission();
        permission.setId(permId);
        permission.setCode("TENANT_READ");
        permission.setDomain("TENANT");
        permission.setDescription("Read tenant");
        permission.setCreatedDate(Instant.now());
        permission.setCreatedBy("seed");

        RolePermission rp = new RolePermission();
        rp.setId(new RolePermissionId(roleId, permId));
        rp.setRole(role);
        rp.setPermission(permission);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(rolePermissionRepository.findAllByRoleId(roleId)).thenReturn(List.of(rp));

        mockMvc.perform(get("/auth/roles/{roleId}/permissions", roleId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("TENANT_READ"));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_READ", "ROLE_INSERT", "ROLE_UPDATE", "ROLE_DELETE", "PERMISSION_READ", "PERMISSION_UPDATE", "ROLE_APP_OWNER"})
    void shouldGetRolePermissionsRoleNotFound() throws Exception {
        UUID roleId = UUID.randomUUID();

        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/auth/roles/{roleId}/permissions", roleId))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Erro de Negócio"));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_READ", "ROLE_INSERT", "ROLE_UPDATE", "ROLE_DELETE", "PERMISSION_READ", "PERMISSION_UPDATE", "ROLE_APP_OWNER"})
    void shouldAssignPermissionsToRole() throws Exception {
        UUID roleId = UUID.randomUUID();
        UUID permId = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(1L);

        Role role = new Role();
        role.setId(roleId);
        role.setName("ADMIN");
        role.setTenant(tenant);

        Permission permission = new Permission();
        permission.setId(permId);
        permission.setCode("TENANT_READ");

        RolePermission savedRp = new RolePermission();
        savedRp.setId(new RolePermissionId(roleId, permId));
        savedRp.setRole(role);
        savedRp.setPermission(permission);
        savedRp.setTenant(tenant);

        RolePermissionRequestDTO request = new RolePermissionRequestDTO(roleId, List.of(permId));

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(rolePermissionRepository.existsById(any())).thenReturn(false);
        when(permissionRepository.findById(permId)).thenReturn(Optional.of(permission));
        when(rolePermissionRepository.save(any())).thenReturn(savedRp);

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));
            utils.when(() -> SecurityUtils.getCorrelationIdFromRequest(any()))
                    .thenReturn(UUID.randomUUID());

            mockMvc.perform(post("/auth/roles/{roleId}/permissions", roleId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser(authorities = {"ROLE_READ", "ROLE_INSERT", "ROLE_UPDATE", "ROLE_DELETE", "PERMISSION_READ", "PERMISSION_UPDATE", "ROLE_APP_OWNER"})
    void shouldAssignPermissionsToRoleMismatch() throws Exception {
        UUID roleId = UUID.randomUUID();
        UUID differentRoleId = UUID.randomUUID();
        UUID permId = UUID.randomUUID();

        RolePermissionRequestDTO request = new RolePermissionRequestDTO(differentRoleId, List.of(permId));

        mockMvc.perform(post("/auth/roles/{roleId}/permissions", roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_READ", "ROLE_INSERT", "ROLE_UPDATE", "ROLE_DELETE", "PERMISSION_READ", "PERMISSION_UPDATE", "ROLE_APP_OWNER"})
    void shouldRemovePermissionFromRole() throws Exception {
        UUID roleId = UUID.randomUUID();
        UUID permId = UUID.randomUUID();

        when(rolePermissionRepository.existsByRoleAndPermission(roleId, permId)).thenReturn(true);

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));
            utils.when(() -> SecurityUtils.getCorrelationIdFromRequest(any()))
                    .thenReturn(UUID.randomUUID());

            mockMvc.perform(delete("/auth/roles/{roleId}/permissions/{permissionId}", roleId, permId))
                    .andDo(print())
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    @WithMockUser(authorities = {"ROLE_READ", "ROLE_INSERT", "ROLE_UPDATE", "ROLE_DELETE", "PERMISSION_READ", "PERMISSION_UPDATE", "ROLE_APP_OWNER"})
    void shouldRemovePermissionFromRoleNotFound() throws Exception {
        UUID roleId = UUID.randomUUID();
        UUID permId = UUID.randomUUID();

        when(rolePermissionRepository.existsByRoleAndPermission(roleId, permId)).thenReturn(false);

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));

            mockMvc.perform(delete("/auth/roles/{roleId}/permissions/{permissionId}", roleId, permId))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Erro de Negócio"));
        }
    }

    @Test
    @WithMockUser(authorities = {"ROLE_READ", "ROLE_INSERT", "ROLE_UPDATE", "ROLE_DELETE", "PERMISSION_READ", "PERMISSION_UPDATE", "ROLE_APP_OWNER"})
    void shouldDeleteRole() throws Exception {
        UUID roleId = UUID.randomUUID();

        Role role = new Role();
        role.setId(roleId);
        role.setName("OBSOLETO");

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(rolePermissionRepository.existsByRoleId(roleId)).thenReturn(false);
        when(userRoleRepository.existsByRoleId(roleId)).thenReturn(false);

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));
            utils.when(() -> SecurityUtils.getCorrelationIdFromRequest(any()))
                    .thenReturn(UUID.randomUUID());

            mockMvc.perform(delete("/auth/roles/{id}/delete", roleId))
                    .andDo(print())
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    @WithMockUser(authorities = {"ROLE_READ", "ROLE_INSERT", "ROLE_UPDATE", "ROLE_DELETE", "PERMISSION_READ", "PERMISSION_UPDATE", "ROLE_APP_OWNER"})
    void shouldDeleteRoleFailWithPermissions() throws Exception {
        UUID roleId = UUID.randomUUID();

        Role role = new Role();
        role.setId(roleId);
        role.setName("COM_PERMISSOES");

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(rolePermissionRepository.existsByRoleId(roleId)).thenReturn(true);

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));

            mockMvc.perform(delete("/auth/roles/{id}/delete", roleId))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Erro de Negócio"));
        }
    }

    @Test
    @WithMockUser(authorities = {"ROLE_READ", "ROLE_INSERT", "ROLE_UPDATE", "ROLE_DELETE", "PERMISSION_READ", "PERMISSION_UPDATE", "ROLE_APP_OWNER"})
    void shouldDeleteRoleFailWithUsers() throws Exception {
        UUID roleId = UUID.randomUUID();

        Role role = new Role();
        role.setId(roleId);
        role.setName("COM_USUARIOS");

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(rolePermissionRepository.existsByRoleId(roleId)).thenReturn(false);
        when(userRoleRepository.existsByRoleId(roleId)).thenReturn(true);

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));

            mockMvc.perform(delete("/auth/roles/{id}/delete", roleId))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Erro de Negócio"));
        }
    }

    @Test
    @WithMockUser(authorities = {"ROLE_READ", "ROLE_INSERT", "ROLE_UPDATE", "ROLE_DELETE", "PERMISSION_READ", "PERMISSION_UPDATE", "ROLE_APP_OWNER"})
    void shouldDeleteRoleNotFound() throws Exception {
        UUID roleId = UUID.randomUUID();

        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));

            mockMvc.perform(delete("/auth/roles/{id}/delete", roleId))
                    .andDo(print())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("Erro de Negócio"));
        }
    }
}
