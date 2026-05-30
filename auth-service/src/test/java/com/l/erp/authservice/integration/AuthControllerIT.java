package com.l.erp.authservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.authservice.api.dto.LoginRequest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.dominio.enumerators.EnumTenantStatus;
import com.l.erp.authservice.repositorios.TenantRepository;
import com.l.erp.authservice.repositorios.UserAccountRepository;
import com.l.erp.authservice.services.EmailNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthControllerIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    EmailNotificationService emailNotificationService;

    private static final String TEST_EMAIL    = "it-user@test.com";
    private static final String TEST_PASSWORD = "Password@123";

    @BeforeEach
    void setUp() {
        userAccountRepository.deleteAll();
        tenantRepository.deleteAll();

        UserAccount user = new UserAccount();
        user.setEmail(TEST_EMAIL);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setDisplayName("IT User");
        user.setUserType("OWNER");
        user.setActive(true);
        user.setFailedLoginAttempts(0);
        user.setCreatedDate(Instant.now());
        user.setCreatedBy("test");
        userAccountRepository.save(user);
    }

    @Test
    void shouldLoginSuccessWithRealDatabase() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(TEST_EMAIL, TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void shouldIncrementFailedAttemptsOnWrongPassword() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(TEST_EMAIL, "WrongPassword"))))
                .andExpect(status().isUnauthorized());

        UserAccount after = userAccountRepository.findByEmail(TEST_EMAIL).orElseThrow();
        assertThat(after.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void shouldLockAccountAfterFiveFailedAttempts() throws Exception {
        LoginRequest bad = new LoginRequest(TEST_EMAIL, "WrongPassword");
        String body = objectMapper.writeValueAsString(bad);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isLocked());

        UserAccount locked = userAccountRepository.findByEmail(TEST_EMAIL).orElseThrow();
        assertThat(locked.getLockedUntil()).isNotNull();
        assertThat(locked.getLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void shouldReturn401ForUnknownUser() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("nobody@test.com", TEST_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }
}