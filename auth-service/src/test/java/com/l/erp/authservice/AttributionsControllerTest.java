package com.l.erp.authservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.authservice.api.controllers.AttributionsController;
import com.l.erp.authservice.api.dto.CurrentUser;
import com.l.erp.authservice.api.dto.UserRoleRequestDTO;
import com.l.erp.authservice.configuration.ObjectMapperConfig;
import com.l.erp.authservice.dominio.Role;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.dominio.UserRole;
import com.l.erp.authservice.dominio.UserRoleId;
import com.l.erp.authservice.repositorios.RoleRepository;
import com.l.erp.authservice.repositorios.UserAccountRepository;
import com.l.erp.authservice.repositorios.UserRoleRepository;
import com.l.erp.authservice.services.AttributionsService;
import com.l.erp.authservice.services.audit.AuditService;
import com.l.erp.authservice.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AttributionsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({AttributionsService.class, ObjectMapperConfig.class})
class AttributionsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private UserRoleRepository userRoleRepository;

    @MockitoBean
    private AuditService auditService;

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldGetRolesByUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setName("Empresa X");

        Role role = new Role();
        role.setId(roleId);
        role.setName("GESTOR");
        role.setTenant(tenant);
        role.setCreatedDate(Instant.now());
        role.setCreatedBy("seed");

        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(1L, userId, roleId));
        userRole.setRole(role);

        UserAccount user = new UserAccount();
        user.setId(userId);

        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRoleRepository.findAllByUserId(userId)).thenReturn(List.of(userRole));

        mockMvc.perform(get("/auth/users/{userId}/roles", userId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("GESTOR"));
    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldGetRolesByUserNotFound() throws Exception {
        UUID userId = UUID.randomUUID();

        when(userAccountRepository.findById(userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/auth/users/{userId}/roles", userId))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Erro de Negocio"));
    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldAssignRolesToUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(1L);

        UserAccount user = new UserAccount();
        user.setId(userId);
        user.setTenant(tenant);

        Role role = new Role();
        role.setId(roleId);
        role.setName("GESTOR");
        role.setTenant(tenant);

        UserRole savedUr = new UserRole();
        savedUr.setId(new UserRoleId(1L, userId, roleId));

        UserRoleRequestDTO request = new UserRoleRequestDTO(userId, List.of(roleId));

        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRoleRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(userRoleRepository.save(any(UserRole.class))).thenReturn(savedUr);

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));
            utils.when(() -> SecurityUtils.getCorrelationIdFromRequest(any()))
                    .thenReturn(UUID.randomUUID());

            mockMvc.perform(post("/auth/users/{userId}/roles", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldAssignRolesToUserMismatch() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID differentUserId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        UserRoleRequestDTO request = new UserRoleRequestDTO(differentUserId, List.of(roleId));

        mockMvc.perform(post("/auth/users/{userId}/roles", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldAssignRolesToUserNotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        UserRoleRequestDTO request = new UserRoleRequestDTO(userId, List.of(roleId));

        when(userAccountRepository.findById(userId)).thenReturn(Optional.empty());

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));

            mockMvc.perform(post("/auth/users/{userId}/roles", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("Erro de Negocio"));
        }
    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldAssignRolesToUserCrossTenantError() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        Tenant tenantA = new Tenant();
        tenantA.setId(1L);

        Tenant tenantB = new Tenant();
        tenantB.setId(2L);

        UserAccount user = new UserAccount();
        user.setId(userId);
        user.setTenant(tenantA);

        Role role = new Role();
        role.setId(roleId);
        role.setName("GESTOR");
        role.setTenant(tenantB);

        UserRoleRequestDTO request = new UserRoleRequestDTO(userId, List.of(roleId));

        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRoleRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));
            utils.when(() -> SecurityUtils.getCorrelationIdFromRequest(any()))
                    .thenReturn(UUID.randomUUID());

            mockMvc.perform(post("/auth/users/{userId}/roles", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Erro de Negocio"));
        }
    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldRemoveRoleFromUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        when(userRoleRepository.existsByUserAndRole(userId, roleId)).thenReturn(true);

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));
            utils.when(() -> SecurityUtils.getCorrelationIdFromRequest(any()))
                    .thenReturn(UUID.randomUUID());

            mockMvc.perform(delete("/auth/users/{userId}/roles/{roleId}", userId, roleId))
                    .andDo(print())
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldRemoveRoleFromUserNotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        when(userRoleRepository.existsByUserAndRole(userId, roleId)).thenReturn(false);

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));

            mockMvc.perform(delete("/auth/users/{userId}/roles/{roleId}", userId, roleId))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Erro de Negocio"));
        }
    }
}
