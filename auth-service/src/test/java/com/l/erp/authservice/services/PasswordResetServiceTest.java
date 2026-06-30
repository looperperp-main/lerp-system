package com.l.erp.authservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.authservice.dominio.PasswordResetToken;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.repositorios.PasswordResetTokenRepository;
import com.l.erp.authservice.repositorios.RefreshTokenRepository;
import com.l.erp.authservice.repositorios.TenantRepository;
import com.l.erp.authservice.repositorios.UserAccountRepository;
import com.l.erp.authservice.services.audit.AuditService;
import com.l.erp.authservice.util.PasswordValidatorUtil;
import com.l.erp.common.exception.custom.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceTest {

    private UserAccountRepository userRepo;
    private TenantRepository tenantRepository;
    private PasswordResetTokenRepository tokenRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordEncoder passwordEncoder;
    private PasswordValidatorUtil passwordValidatorUtil;
    private AuditService auditService;
    private KafkaTemplate<String, String> kafkaTemplate;
    private PasswordResetService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userRepo = mock(UserAccountRepository.class);
        tenantRepository = mock(TenantRepository.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        passwordValidatorUtil = mock(PasswordValidatorUtil.class);
        auditService = mock(AuditService.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        service = new PasswordResetService(userRepo, tenantRepository, tokenRepository, refreshTokenRepository,
                passwordEncoder, passwordValidatorUtil, auditService, kafkaTemplate, new ObjectMapper());
    }

    private Tenant tenant() {
        Tenant t = new Tenant();
        t.setId(1L);
        t.setName("Empresa X");
        return t;
    }

    private UserAccount user() {
        UserAccount u = new UserAccount();
        u.setId(UUID.randomUUID());
        u.setEmail("user@empresa.com");
        u.setDisplayName("User");
        u.setTenant(tenant());
        return u;
    }

    // ---- Solicitação ----

    @Test
    void solicitar_userExists_savesTokenAndPublishesEmail() {
        when(tenantRepository.findByCnpj(anyString())).thenReturn(Optional.of(tenant()));
        when(userRepo.findByEmailAndTenantId(anyString(), anyLong())).thenReturn(Optional.of(user()));

        service.solicitarResetTenant("user@empresa.com", "12.345.678/0001-99");

        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(kafkaTemplate).send(eq(EmailNotificationService.EMAIL_TOPIC), anyString(), anyString());
    }

    @Test
    void solicitar_tenantNotFound_noop() {
        when(tenantRepository.findByCnpj(anyString())).thenReturn(Optional.empty());

        service.solicitarResetTenant("user@empresa.com", "00000000000000");

        verify(tokenRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void solicitar_userNotFound_noop() {
        when(tenantRepository.findByCnpj(anyString())).thenReturn(Optional.of(tenant()));
        when(userRepo.findByEmailAndTenantId(anyString(), anyLong())).thenReturn(Optional.empty());

        service.solicitarResetTenant("naoexiste@empresa.com", "12345678000199");

        verify(tokenRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void solicitar_recentTokenWithinCooldown_skipsEmail() {
        when(tenantRepository.findByCnpj(anyString())).thenReturn(Optional.of(tenant()));
        when(userRepo.findByEmailAndTenantId(anyString(), anyLong())).thenReturn(Optional.of(user()));
        PasswordResetToken recent = new PasswordResetToken();
        recent.setCreatedAt(Instant.now().minusSeconds(30)); // < 120s
        when(tokenRepository.findFirstByUser_IdOrderByCreatedAtDesc(any())).thenReturn(Optional.of(recent));

        service.solicitarResetTenant("user@empresa.com", "12345678000199");

        verify(tokenRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void solicitar_oldTokenBeyondCooldown_sendsNew() {
        when(tenantRepository.findByCnpj(anyString())).thenReturn(Optional.of(tenant()));
        when(userRepo.findByEmailAndTenantId(anyString(), anyLong())).thenReturn(Optional.of(user()));
        PasswordResetToken old = new PasswordResetToken();
        old.setCreatedAt(Instant.now().minusSeconds(300)); // > 120s
        when(tokenRepository.findFirstByUser_IdOrderByCreatedAtDesc(any())).thenReturn(Optional.of(old));

        service.solicitarResetTenant("user@empresa.com", "12345678000199");

        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(kafkaTemplate).send(eq(EmailNotificationService.EMAIL_TOPIC), anyString(), anyString());
    }

    // ---- Redefinição ----

    @Test
    void redefinir_passwordsMismatch_throws() {
        assertThatThrownBy(() -> service.redefinirSenha("tok", "Senha@Forte123456", "Outra@Coisa123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não conferem");
        verify(tokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    void redefinir_tokenNotFound_throws() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.redefinirSenha("tok", "Senha@Forte123456", "Senha@Forte123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inválido ou expirado");
    }

    @Test
    void redefinir_tokenUsed_throws() {
        PasswordResetToken prt = new PasswordResetToken();
        prt.setUser(user());
        prt.setUsedAt(Instant.now());
        prt.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(prt));

        assertThatThrownBy(() -> service.redefinirSenha("tok", "Senha@Forte123456", "Senha@Forte123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inválido ou expirado");
    }

    @Test
    void redefinir_tokenExpired_throws() {
        PasswordResetToken prt = new PasswordResetToken();
        prt.setUser(user());
        prt.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(prt));

        assertThatThrownBy(() -> service.redefinirSenha("tok", "Senha@Forte123456", "Senha@Forte123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inválido ou expirado");
    }

    @Test
    void redefinir_happy_setsPasswordRevokesSessionsResetsLockout() {
        UserAccount u = user();
        u.setFailedLoginAttempts(5);
        u.setLockedUntil(Instant.now().plus(1, ChronoUnit.HOURS));

        PasswordResetToken prt = new PasswordResetToken();
        prt.setUser(u);
        prt.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(prt));
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED");

        service.redefinirSenha("tok", "Senha@Forte123456", "Senha@Forte123456");

        verify(passwordValidatorUtil).validatePassword("Senha@Forte123456", "Empresa X");
        assertThat(u.getPasswordHash()).isEqualTo("HASHED");
        assertThat(u.getFailedLoginAttempts()).isZero();
        assertThat(u.getLockedUntil()).isNull();
        assertThat(prt.getUsedAt()).isNotNull();
        verify(userRepo).save(u);
        verify(refreshTokenRepository).revokeAllActiveByUserId(eq(u.getId()), any(Instant.class));

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUsedAt()).isNotNull();
    }
}