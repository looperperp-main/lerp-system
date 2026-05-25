package com.l.erp.authservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.authservice.api.controllers.AuthController;
import com.l.erp.authservice.api.dto.LoginRequest;
import com.l.erp.authservice.api.dto.LoginResponse;
import com.l.erp.authservice.api.dto.LogoutRequest;
import com.l.erp.authservice.api.dto.RefreshRequest;
import com.l.erp.authservice.api.dto.TenantLoginRequest;
import com.l.erp.authservice.api.dto.TenantLoginResponse;
import com.l.erp.authservice.api.mappers.AuthMapper;
import com.l.erp.authservice.configuration.ObjectMapperConfig;
import com.l.erp.authservice.dominio.RefreshToken;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.dominio.enumerators.EnumTenantStatus;
import com.l.erp.authservice.infra.AuthService;
import com.l.erp.authservice.infra.RefreshTokenService;
import com.l.erp.authservice.infra.TokenService;
import com.l.erp.authservice.repositorios.OwnerMarkerRepository;
import com.l.erp.authservice.repositorios.RolePermissionRepository;
import com.l.erp.authservice.repositorios.TenantRepository;
import com.l.erp.authservice.repositorios.UserAccountRepository;
import com.l.erp.authservice.repositorios.UserRoleRepository;
import com.l.erp.authservice.services.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({AuthService.class, ObjectMapperConfig.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @MockitoBean
    private OwnerMarkerRepository ownerMarkerRepository;

    @MockitoBean
    private TenantRepository tenantRepository;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AuthMapper authMapper;

    @MockitoBean
    private UserRoleRepository userRoleRepository;

    @MockitoBean
    private RolePermissionRepository rolePermissionRepository;

    @MockitoBean
    private AuditService auditService;

    @Test
    void shouldLoginSuccess() throws Exception {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setActive(true);
        user.setFailedLoginAttempts(0);

        RefreshToken refreshEntity = new RefreshToken();
        refreshEntity.setUser(user);

        LoginRequest request = new LoginRequest("user@test.com", "Password@123");
        LoginResponse response = new LoginResponse("user@test.com", "jwt-token", "refresh-token");

        when(userAccountRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(ownerMarkerRepository.existsByUser_IdAndEnabledTrue(any())).thenReturn(false);
        when(userRoleRepository.findAllByUserId(any())).thenReturn(List.of());
        when(tokenService.generateToken(any(), any(), anyBoolean(), any())).thenReturn("jwt-token");
        when(refreshTokenService.issue(any(),any())).thenReturn(new RefreshTokenService.TokenPair("refresh-token", refreshEntity));
        when(authMapper.toLoginResponse(any(), any(), any())).thenReturn(response);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void shouldLoginFailUserNotFound() throws Exception {
        LoginRequest request = new LoginRequest("nouser@test.com", "Password@123");

        when(userAccountRepository.findByEmail("nouser@test.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLoginFailUserInactive() throws Exception {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setActive(false);
        user.setFailedLoginAttempts(0);

        LoginRequest request = new LoginRequest("user@test.com", "Password@123");

        when(userAccountRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLoginFailWrongPassword() throws Exception {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setActive(true);
        user.setFailedLoginAttempts(0);

        LoginRequest request = new LoginRequest("user@test.com", "WrongPassword");

        when(userAccountRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLoginLockedUser() throws Exception {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setActive(true);
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(Instant.now().plusSeconds(3600));

        LoginRequest request = new LoginRequest("user@test.com", "Password@123");

        when(userAccountRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isLocked());
    }

    @Test
    void shouldTenantLoginSuccess() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setName("Empresa X");
        tenant.setStatus(EnumTenantStatus.ATIVO);

        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setActive(true);
        user.setFailedLoginAttempts(0);

        RefreshToken refreshEntity = new RefreshToken();
        refreshEntity.setUser(user);

        TenantLoginRequest request = new TenantLoginRequest("12345678000190", "user@test.com", "Password@123");
        TenantLoginResponse response = new TenantLoginResponse("user@test.com", "tenant-jwt", "refresh-token", 1L, "Empresa X", "12345678000190");

        when(tenantRepository.findByCnpj("12345678000190")).thenReturn(Optional.of(tenant));
        when(userAccountRepository.findByEmailAndTenantId(anyString(), anyLong())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(userRoleRepository.findAllByUserId(any())).thenReturn(List.of());
        when(tokenService.generateTenantUserToken(any(), any(), any())).thenReturn("tenant-jwt");
        when(refreshTokenService.issue(any(),any())).thenReturn(new RefreshTokenService.TokenPair("refresh-token", refreshEntity));
        when(authMapper.toTenantLoginResponse(any(), any(), any(), any())).thenReturn(response);

        mockMvc.perform(post("/auth/tenant/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tenant-jwt"));
    }

    @Test
    void shouldTenantLoginFailTenantNotFound() throws Exception {
        TenantLoginRequest request = new TenantLoginRequest("99999999000199", "user@test.com", "Password@123");

        when(tenantRepository.findByCnpj("99999999000199")).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/tenant/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldTenantLoginFailTenantInactive() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setName("Empresa X");
        tenant.setStatus(EnumTenantStatus.CANCELADO);

        TenantLoginRequest request = new TenantLoginRequest("12345678000190", "user@test.com", "Password@123");

        when(tenantRepository.findByCnpj("12345678000190")).thenReturn(Optional.of(tenant));

        mockMvc.perform(post("/auth/tenant/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldTenantLoginFailUserNotFound() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setName("Empresa X");
        tenant.setStatus(EnumTenantStatus.ATIVO);

        TenantLoginRequest request = new TenantLoginRequest("12345678000190", "nouser@test.com", "Password@123");

        when(tenantRepository.findByCnpj("12345678000190")).thenReturn(Optional.of(tenant));
        when(userAccountRepository.findByEmailAndTenantId(anyString(), anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/tenant/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRefreshSuccess() throws Exception {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setFailedLoginAttempts(0);

        RefreshToken rt = new RefreshToken();
        rt.setUser(user);

        RefreshToken newRtEntity = new RefreshToken();
        newRtEntity.setUser(user);

        RefreshRequest request = new RefreshRequest("valid-refresh-token");

        when(refreshTokenService.findValid("valid-refresh-token")).thenReturn(Optional.of(rt));
        when(ownerMarkerRepository.existsByUser_IdAndEnabledTrue(any())).thenReturn(false);
        when(userRoleRepository.findAllByUserId(any())).thenReturn(List.of());
        when(tokenService.generateToken(any(), any(), anyBoolean(), any())).thenReturn("new-jwt-token");
        when(refreshTokenService.issue(any(),any())).thenReturn(new RefreshTokenService.TokenPair("new-refresh-token", newRtEntity));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-jwt-token"));
    }

    @Test
    void shouldRefreshFailInvalidToken() throws Exception {
        RefreshRequest request = new RefreshRequest("invalid-token");

        when(refreshTokenService.findValid("invalid-token")).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldLogoutSuccess() throws Exception {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());

        RefreshToken rt = new RefreshToken();
        rt.setUser(user);

        LogoutRequest request = new LogoutRequest("valid-token");

        when(refreshTokenService.findValid("valid-token")).thenReturn(Optional.of(rt));

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldLogoutTokenNotFound() throws Exception {
        LogoutRequest request = new LogoutRequest("unknown-token");

        when(refreshTokenService.findValid("unknown-token")).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNoContent());
    }
}
