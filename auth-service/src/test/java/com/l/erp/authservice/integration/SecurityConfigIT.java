package com.l.erp.authservice.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Valida o permitAll real de SecurityConfig (não a WebSecurityConfig de teste
 * usada pelos @WebMvcTest) para os paths de actuator liberados pro scrape do
 * Prometheus e health checks.
 */
@AutoConfigureMockMvc
class SecurityConfigIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void actuatorHealth_passaSemAutenticacao() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorInfo_passaSemAutenticacao() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorPrometheus_passaSemAutenticacao() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }
}