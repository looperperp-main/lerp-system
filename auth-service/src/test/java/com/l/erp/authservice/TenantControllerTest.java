package com.l.erp.authservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.authservice.api.controllers.TenantController;
import com.l.erp.authservice.api.dto.TenantDTO;
import com.l.erp.authservice.configuration.ObjectMapperConfig;
import com.l.erp.authservice.services.TenantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({TenantController.class, ObjectMapperConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(MockitoExtension.class)
public class TenantControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    @InjectMocks
    private TenantService tenantService;

    @Autowired
    private ObjectMapper objectMapper;

    @Mock
    Logger mockLogger;

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

        var TOKEN_ATTR_NAME = "_csrf";
        var httpSessionCsrfTokenRepository = new HttpSessionCsrfTokenRepository();
        var csrfToken = httpSessionCsrfTokenRepository.generateToken(new MockHttpServletRequest());

        TenantDTO input = new TenantDTO(null, "Empresa Y", "98765432000110", "ATIVO",
                Instant.now(),"admin", Instant.now(),"admin");

        TenantDTO created = new TenantDTO(2L, "Empresa Y", "98765432000110", "ATIVO",
                Instant.now(),"admin", Instant.now(),"admin");

        when(tenantService.createTenant(input)).thenCallRealMethod();

        mockMvc.perform(post("/auth/tenants")
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .content(objectMapper.writeValueAsString(input))
                        .sessionAttr(TOKEN_ATTR_NAME, csrfToken)
                        .param(csrfToken.getParameterName(), csrfToken.getToken()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.name").value("Empresa Y"));
    }

}
