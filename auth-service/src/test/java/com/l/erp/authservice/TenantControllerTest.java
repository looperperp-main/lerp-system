package com.l.erp.authservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.authservice.api.controllers.TenantController;
import com.l.erp.authservice.api.dto.TenantDTO;
import com.l.erp.authservice.configuration.ObjectMapperConfig;
import com.l.erp.authservice.infra.config.Roles;
import com.l.erp.authservice.services.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({TenantController.class, ObjectMapperConfig.class})
public class TenantControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TenantService tenantService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldReturnTenants() throws Exception {
        TenantDTO dto = new TenantDTO(1L, "Empresa X", "12345678000190", "ATIVO",
                 Instant.now(),"admin", Instant.now(),"admin");

        when(tenantService.getAllTenants()).thenReturn(List.of(dto));

        mockMvc.perform(get("/auth/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].name").value("Empresa X"));
    }

    @Test
    void shouldCreateTenant() throws Exception {
        TenantDTO input = new TenantDTO(null, "Empresa Y", "98765432000110", "ATIVO",
                Instant.now(),"admin", Instant.now(),"admin");

        TenantDTO created = new TenantDTO(2L, "Empresa Y", "98765432000110", "ATIVO",
                Instant.now(),"admin", Instant.now(),"admin");

        when(tenantService.createTenant(input)).thenReturn(created);

        var auth = new UsernamePasswordAuthenticationToken(
                "user-id", null, List.of(new SimpleGrantedAuthority(Roles.APP_OWNER))
        );
        auth.setDetails(Map.of("email", "admin@teste.com"));

        mockMvc.perform(post("/auth/tenants")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.name").value("Empresa Y"));
    }

    @Test
    void shouldRejectWithoutRole() throws Exception {
        mockMvc.perform(get("/auth/tenants"))
                .andExpect(status().isUnauthorized());
    }
}
