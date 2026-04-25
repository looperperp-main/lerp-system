package com.l.erp.authservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.authservice.api.controllers.PermissionController;
import com.l.erp.authservice.api.dto.CurrentUser;
import com.l.erp.authservice.api.dto.PermissionDTO;
import com.l.erp.authservice.api.mappers.PermissionMapper;
import com.l.erp.authservice.configuration.ObjectMapperConfig;
import com.l.erp.authservice.dominio.Permission;
import com.l.erp.authservice.repositorios.PermissionRepository;
import com.l.erp.authservice.services.PermissionService;
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

@WebMvcTest(controllers = PermissionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({PermissionService.class, ObjectMapperConfig.class})
class PermissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PermissionRepository permissionRepository;

    @MockitoBean
    private PermissionMapper permissionMapper;

    @MockitoBean
    private AuditService auditService;

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldGetAllPermissions() throws Exception {
        Permission permission = new Permission();
        permission.setId(UUID.randomUUID());
        permission.setCode("TENANT_READ");

        PermissionDTO dto = new PermissionDTO(permission.getId(), "TENANT_READ", "TENANT",
                "Read tenants", Instant.now(), "seed", null, null);

        Page<Permission> page = new PageImpl<>(List.of(permission));
        when(permissionRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(permissionMapper.toPermissionDTO(any(Permission.class))).thenReturn(dto);

        mockMvc.perform(get("/auth/permissions?page=0&size=10"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldCreatePermission() throws Exception {
        UUID permId = UUID.randomUUID();

        Permission saved = new Permission();
        saved.setId(permId);
        saved.setCode("TENANT_INSERT");
        saved.setDomain("TENANT");

        PermissionDTO input = new PermissionDTO(null, "TENANT_INSERT", "TENANT",
                "Insert tenant", null, null, null, null);
        PermissionDTO output = new PermissionDTO(permId, "TENANT_INSERT", "TENANT",
                "Insert tenant", Instant.now(), "test@test.com", null, null);

        when(permissionRepository.findByCode("TENANT_INSERT")).thenReturn(Optional.empty());
        when(permissionRepository.save(any(Permission.class))).thenReturn(saved);
        when(permissionMapper.toPermissionDTO(saved)).thenReturn(output);

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));
            utils.when(() -> SecurityUtils.getCorrelationIdFromRequest(any()))
                    .thenReturn(UUID.randomUUID());

            mockMvc.perform(post("/auth/permissions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(input)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value("TENANT_INSERT"));
        }
    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldCreatePermissionDuplicated() throws Exception {
        Permission existing = new Permission();
        existing.setId(UUID.randomUUID());
        existing.setCode("TENANT_INSERT");

        PermissionDTO input = new PermissionDTO(null, "TENANT_INSERT", "TENANT",
                "Insert tenant", null, null, null, null);

        when(permissionRepository.findByCode("TENANT_INSERT")).thenReturn(Optional.of(existing));

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));

            mockMvc.perform(post("/auth/permissions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(input)))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Erro de Negocio"));
        }
    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldGetPermissionById() throws Exception {
        UUID permId = UUID.randomUUID();

        Permission permission = new Permission();
        permission.setId(permId);
        permission.setCode("TENANT_READ");
        permission.setDomain("TENANT");

        PermissionDTO dto = new PermissionDTO(permId, "TENANT_READ", "TENANT",
                "Read tenant", Instant.now(), "seed", null, null);

        when(permissionRepository.findById(permId)).thenReturn(Optional.of(permission));
        when(permissionMapper.toPermissionDTO(permission)).thenReturn(dto);

        mockMvc.perform(get("/auth/permissions/{permissionId}", permId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TENANT_READ"));
    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldGetPermissionByIdNotFound() throws Exception {
        UUID permId = UUID.randomUUID();

        when(permissionRepository.findById(permId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/auth/permissions/{permissionId}", permId))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Erro de Negocio"));
    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldUpdatePermission() throws Exception {
        UUID permId = UUID.randomUUID();

        Permission existing = new Permission();
        existing.setId(permId);
        existing.setCode("TENANT_READ");
        existing.setDomain("TENANT");
        existing.setDescription("Old description");

        Permission updated = new Permission();
        updated.setId(permId);
        updated.setCode("TENANT_READ");
        updated.setDomain("TENANT");
        updated.setDescription("New description");

        PermissionDTO input = new PermissionDTO(permId, "TENANT_READ", "TENANT",
                "New description", null, null, null, null);
        PermissionDTO output = new PermissionDTO(permId, "TENANT_READ", "TENANT",
                "New description", Instant.now(), "seed", Instant.now(), "test@test.com");

        when(permissionRepository.findById(permId)).thenReturn(Optional.of(existing));
        when(permissionRepository.findByCode("TENANT_READ")).thenReturn(Optional.empty());
        when(permissionRepository.save(any(Permission.class))).thenReturn(updated);
        when(permissionMapper.toPermissionDTO(updated)).thenReturn(output);

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));
            utils.when(() -> SecurityUtils.getCorrelationIdFromRequest(any()))
                    .thenReturn(UUID.randomUUID());

            mockMvc.perform(put("/auth/permissions/{permissionId}", permId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(input)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.description").value("New description"));
        }
    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldUpdatePermissionCodeDuplicated() throws Exception {
        UUID permId = UUID.randomUUID();

        Permission existing = new Permission();
        existing.setId(permId);
        existing.setCode("OLD_CODE");
        existing.setDomain("TENANT");

        Permission another = new Permission();
        another.setId(UUID.randomUUID());
        another.setCode("NEW_CODE");

        PermissionDTO input = new PermissionDTO(permId, "NEW_CODE", "TENANT",
                "Some description", null, null, null, null);

        when(permissionRepository.findById(permId)).thenReturn(Optional.of(existing));
        when(permissionRepository.findByCode("NEW_CODE")).thenReturn(Optional.of(another));

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));

            mockMvc.perform(put("/auth/permissions/{permissionId}", permId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(input)))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Erro de Negocio"));
        }
    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldDeletePermission() throws Exception {
        UUID permId = UUID.randomUUID();

        Permission permission = new Permission();
        permission.setId(permId);
        permission.setCode("TENANT_DELETE");

        when(permissionRepository.findById(permId)).thenReturn(Optional.of(permission));

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));
            utils.when(() -> SecurityUtils.getCorrelationIdFromRequest(any()))
                    .thenReturn(UUID.randomUUID());

            mockMvc.perform(delete("/auth/permissions/{permissionId}", permId))
                    .andDo(print())
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldDeletePermissionNotFound() throws Exception {
        UUID permId = UUID.randomUUID();

        when(permissionRepository.findById(permId)).thenReturn(Optional.empty());

        try (MockedStatic<SecurityUtils> utils = Mockito.mockStatic(SecurityUtils.class)) {
            utils.when(SecurityUtils::getCurrentUserInfo)
                    .thenReturn(new CurrentUser(UUID.randomUUID(), "test@test.com"));

            mockMvc.perform(delete("/auth/permissions/{permissionId}", permId))
                    .andDo(print())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("Erro de Negocio"));
        }
    }
}
