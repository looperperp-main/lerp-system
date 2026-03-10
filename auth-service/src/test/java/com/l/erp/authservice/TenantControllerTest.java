package com.l.erp.authservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.authservice.api.controllers.TenantController;
import com.l.erp.authservice.api.dto.CurrentUser;
import com.l.erp.authservice.api.dto.TenantDTO;
import com.l.erp.authservice.api.mappers.AuthMapper;
import com.l.erp.authservice.configuration.ObjectMapperConfig;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.repositorios.TenantRepository;
import com.l.erp.authservice.services.TenantService;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TenantController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({TenantService.class, ObjectMapperConfig.class})
public class TenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TenantRepository tenantRepository;

    @MockitoBean
    private AuthMapper authMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuditService auditService;

    @Test
    @WithMockUser(authorities = "TENANT_READ")
    void shouldReturnTenants() throws Exception {
        TenantDTO dto = new TenantDTO(1L, "Empresa X", "12345678000190", "ATIVO",
                Instant.now(), "admin", Instant.now(), "admin");

        // Mock para retornar uma Página em vez de lista simples
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setName("Empresa X");
        Page<Tenant> page = new PageImpl<>(List.of(tenant));

        when(tenantRepository.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/auth/tenants?page=0&size=10"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "TENANT_READ")
    void shouldReturnActiveTenants() throws Exception {

        // Mock para retornar uma Página em vez de lista simples
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setName("Empresa X");
        tenant.setStatus("ATIVO");
        Page<Tenant> page = new PageImpl<>(List.of(tenant));

        when(tenantRepository.findAllByStatusIs(anyString(),any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/auth/tenants/active?page=0&size=10"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "TENANT_INSERT")
    void shouldCreateTenant() throws Exception {
        var TOKEN_ATTR_NAME = "_csrf";
        var httpSessionCsrfTokenRepository = new HttpSessionCsrfTokenRepository();
        var csrfToken = httpSessionCsrfTokenRepository.generateToken(new MockHttpServletRequest());

        TenantDTO input = new TenantDTO(null, "Empresa Y", "98765432000110", "ATIVO",
                Instant.now(), "admin", Instant.now(), "admin");

        TenantDTO created = new TenantDTO(2L, "Empresa Y", "98765432000110", "ATIVO",
                Instant.now(), "admin", Instant.now(), "admin");

        Tenant entity = new Tenant();
        Tenant saved = new Tenant();
        saved.setId(2L);

        when(authMapper.toTenant(input)).thenReturn(entity);
        when(tenantRepository.save(any(Tenant.class))).thenReturn(saved);
        when(authMapper.toTenantDTO(saved)).thenReturn(created);

        try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserInfo).thenReturn(new CurrentUser(UUID.randomUUID(),"usuario@teste.com"));


            mockMvc.perform(post("/auth/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(input))
                            .sessionAttr(TOKEN_ATTR_NAME, csrfToken)
                            .param(csrfToken.getParameterName(), csrfToken.getToken()))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(2L))
                    .andExpect(jsonPath("$.name").value("Empresa Y"));
        }
    }

    @Test
    @WithMockUser(authorities = "TENANT_READ")
    void shouldReturnAGivenTenants() throws Exception {
        TenantDTO dto = new TenantDTO(1L, "Empresa X", "12345678000190", "ATIVO",
                Instant.now(), "admin", Instant.now(), "admin");

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(new Tenant()));
        when(authMapper.toTenantDTO(any())).thenReturn(dto);

        mockMvc.perform(get("/auth/tenants/{tenantId}",1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Empresa X"));
    }

    @Test
    @WithMockUser(authorities = "TENANT_UPDATE")
    void shouldUpdateTenantSuccess() throws Exception {
        var TOKEN_ATTR_NAME = "_csrf";
        var httpSessionCsrfTokenRepository = new HttpSessionCsrfTokenRepository();
        var csrfToken = httpSessionCsrfTokenRepository.generateToken(new MockHttpServletRequest());

        TenantDTO originalUpdated = new TenantDTO(1L, "Empresa Z", "98765432000110", "ATIVO",
                Instant.now(), "admin", Instant.now(), "admin");

        Tenant oldTenant = new Tenant();
        oldTenant.setId(1L);
        oldTenant.setName("Empresa X");
        oldTenant.setStatus("ATIVO");

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(oldTenant));
        when(tenantRepository.countAllByNameAndCnpj(any(),any())).thenReturn(0L);

        Tenant updated = new Tenant();
        updated.setId(1L);
        updated.setName("Empresa Z");
        when(tenantRepository.save(any(Tenant.class))).thenReturn(updated);
        when(authMapper.toTenant(any())).thenReturn(updated);
        when(authMapper.toTenantDTO(updated)).thenReturn(originalUpdated);

        try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserInfo).thenReturn(new CurrentUser(UUID.randomUUID(),"usuario@teste.com"));


            mockMvc.perform(put("/auth/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(originalUpdated))
                            .sessionAttr(TOKEN_ATTR_NAME, csrfToken)
                            .param(csrfToken.getParameterName(), csrfToken.getToken()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1L))
                    .andExpect(jsonPath("$.name").value("Empresa Z"));
        }
    }

    @Test
    @WithMockUser(authorities = "TENANT_UPDATE")
    void shouldntUpdateTenantSuccess() throws Exception {
        var TOKEN_ATTR_NAME = "_csrf";
        var httpSessionCsrfTokenRepository = new HttpSessionCsrfTokenRepository();
        var csrfToken = httpSessionCsrfTokenRepository.generateToken(new MockHttpServletRequest());

        TenantDTO originalUpdated = new TenantDTO(1L, "Empresa Z", "98765432000110", "ATIVO",
                Instant.now(), "admin", Instant.now(), "admin");

        Tenant oldTenant = new Tenant();
        oldTenant.setId(1L);
        oldTenant.setName("Empresa X");
        oldTenant.setStatus("ATIVO");

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(oldTenant));
        when(tenantRepository.countAllByNameAndCnpj(any(),any())).thenReturn(1L);

        Tenant updated = new Tenant();
        updated.setId(1L);
        updated.setName("Empresa Z");
        when(tenantRepository.save(any(Tenant.class))).thenReturn(updated);
        when(authMapper.toTenant(any())).thenReturn(updated);
        when(authMapper.toTenantDTO(updated)).thenReturn(originalUpdated);

        try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserInfo).thenReturn(new CurrentUser(UUID.randomUUID(),"usuario@teste.com"));


            mockMvc.perform(put("/auth/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(originalUpdated))
                            .sessionAttr(TOKEN_ATTR_NAME, csrfToken)
                            .param(csrfToken.getParameterName(), csrfToken.getToken()))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Erro de Negocio"))
                    .andExpect(jsonPath("$.message").value("Tenant : Registro em duplicidade"));
        }
    }

    @Test
    @WithMockUser(authorities = "TENANT_UPDATE")
    void shouldUpdateTenantErrorCancelado() throws Exception {
        var TOKEN_ATTR_NAME = "_csrf";
        var httpSessionCsrfTokenRepository = new HttpSessionCsrfTokenRepository();
        var csrfToken = httpSessionCsrfTokenRepository.generateToken(new MockHttpServletRequest());

        TenantDTO originalUpdated = new TenantDTO(1L, "Empresa Z", "98765432000110", "ATIVO",
                Instant.now(), "admin", Instant.now(), "admin");

        Tenant oldTenant = new Tenant();
        oldTenant.setId(1L);
        oldTenant.setName("Empresa X");
        oldTenant.setStatus("CANCELADO");

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(oldTenant));
        when(tenantRepository.countAllByNameAndCnpj(any(),any())).thenReturn(1L);



        try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserInfo).thenReturn(new CurrentUser(UUID.randomUUID(),"usuario@teste.com"));


            mockMvc.perform(put("/auth/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(originalUpdated))
                            .sessionAttr(TOKEN_ATTR_NAME, csrfToken)
                            .param(csrfToken.getParameterName(), csrfToken.getToken()))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Erro de Negocio"));
        }
    }

    @Test
    @WithMockUser(authorities = "TENANT_DELETE")
    void updateTenantStatusById() throws Exception {
        var TOKEN_ATTR_NAME = "_csrf";
        var httpSessionCsrfTokenRepository = new HttpSessionCsrfTokenRepository();
        var csrfToken = httpSessionCsrfTokenRepository.generateToken(new MockHttpServletRequest());

        TenantDTO originalUpdated = new TenantDTO(1L, "Empresa Z", "98765432000110", "ATIVO",
                Instant.now(), "admin", Instant.now(), "admin");

        Tenant oldTenant = new Tenant();
        oldTenant.setId(1L);
        oldTenant.setName("Empresa X");
        oldTenant.setStatus("CANCELADO");

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(oldTenant));
        when(tenantRepository.countAllByNameAndCnpj(any(),any())).thenReturn(1L);



        try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserEmail).thenReturn(Optional.of("usuario@teste.com"));
            securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(Optional.of(UUID.randomUUID()));

            mockMvc.perform(patch("/auth/tenants/{tenantId}/status",1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString("CANCELADO"))
                            .sessionAttr(TOKEN_ATTR_NAME, csrfToken)
                            .param(csrfToken.getParameterName(), csrfToken.getToken()))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Erro de Negocio"));
        }
    }

    @Test
    @WithMockUser(authorities = "TENANT_DELETE")
    void updateTenantStatusByIdToSuspenso() throws Exception {
        var TOKEN_ATTR_NAME = "_csrf";
        var httpSessionCsrfTokenRepository = new HttpSessionCsrfTokenRepository();
        var csrfToken = httpSessionCsrfTokenRepository.generateToken(new MockHttpServletRequest());

        Tenant oldTenant = new Tenant();
        oldTenant.setId(1L);
        oldTenant.setName("Empresa X");
        oldTenant.setStatus("ATIVO");

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(oldTenant));
        when(tenantRepository.countAllByNameAndCnpj(any(),any())).thenReturn(1L);



        try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserInfo).thenReturn(new CurrentUser(UUID.randomUUID(),"usuario@teste.com"));

            mockMvc.perform(patch("/auth/tenants/{tenantId}/status",1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"CANCELADO\"}")
                            .sessionAttr(TOKEN_ATTR_NAME, csrfToken)
                            .param(csrfToken.getParameterName(), csrfToken.getToken()))
                    .andDo(print())
                    .andExpect(status().isNoContent());
        }
    }

}