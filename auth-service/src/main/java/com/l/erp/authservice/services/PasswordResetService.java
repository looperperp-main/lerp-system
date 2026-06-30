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
import com.l.erp.common.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Fluxo "esqueci minha senha" do portal do tenant.
 *
 * <p>Token aleatório single-use guardado só como hash SHA-256 (padrão do refresh token), expira em
 * 30min. A solicitação responde sempre 200 genérico (anti-enumeração). A redefinição valida o token,
 * troca a senha, revoga sessões e zera o lockout.</p>
 */
@Service
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_TTL_HOURS = 1;
    private static final int THROTTLE_SECONDS = 120;

    private final UserAccountRepository userRepo;
    private final TenantRepository tenantRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidatorUtil passwordValidatorUtil;
    private final AuditService auditService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PasswordResetService(UserAccountRepository userRepo,
                                TenantRepository tenantRepository,
                                PasswordResetTokenRepository tokenRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                PasswordEncoder passwordEncoder,
                                PasswordValidatorUtil passwordValidatorUtil,
                                AuditService auditService,
                                KafkaTemplate<String, String> kafkaTemplate,
                                ObjectMapper objectMapper) {
        this.userRepo = userRepo;
        this.tenantRepository = tenantRepository;
        this.tokenRepository = tokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordValidatorUtil = passwordValidatorUtil;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Solicita reset para um usuário de tenant (resolve por CNPJ + e-mail). Responde sempre 200:
     * se o usuário existir, gera token e dispara e-mail; senão, no-op silencioso (anti-enumeração).
     */
    @Transactional
    public void solicitarResetTenant(String email, String cnpj) {
        String cnpjDigits = cnpj == null ? "" : cnpj.replaceAll("\\D", "");

        Tenant tenant = tenantRepository.findByCnpj(cnpjDigits).orElse(null);
        if (tenant == null) {
            logger.info("Reset solicitado para CNPJ inexistente — resposta genérica");
            return;
        }

        UserAccount user = userRepo.findByEmailAndTenantId(email, tenant.getId()).orElse(null);
        if (user == null) {
            logger.info("Reset solicitado para e-mail inexistente no tenant {} — resposta genérica", tenant.getId());
            return;
        }

        // Throttle: se já houve um pedido válido (não usado) nos últimos 120s, reaproveita o que está
        // em voo — não gera token novo nem dispara outro e-mail. Evita e-mail bombing / abuso de cota SMTP.
        PasswordResetToken recent = tokenRepository.findFirstByUser_IdOrderByCreatedAtDesc(user.getId()).orElse(null);
        if (recent != null && recent.getUsedAt() == null
                && recent.getCreatedAt().isAfter(Instant.now().minusSeconds(THROTTLE_SECONDS))) {
            logger.info("Reset re-solicitado em <{}s para o usuário {} — reaproveitando o pedido em voo (sem novo e-mail)",
                    THROTTLE_SECONDS, user.getId());
            return;
        }

        String rawToken = UUID.randomUUID().toString() + UUID.randomUUID();
        PasswordResetToken prt = new PasswordResetToken();
        prt.setUser(user);
        prt.setTokenHash(sha256Hex(rawToken));
        prt.setCreatedAt(Instant.now());
        prt.setExpiresAt(Instant.now().plus(TOKEN_TTL_HOURS, ChronoUnit.HOURS));
        tokenRepository.save(prt);

        publishResetEmail(user.getEmail(), user.getDisplayName(), rawToken);

        auditService.logAuditEventWithActor("PASSWORD_RESET_REQUESTED", user.getId(), Constants.USER,
                user.getId(), Constants.SUCCESS, "Reset de senha solicitado", null);
    }

    /**
     * Redefine a senha a partir do token. Valida senhas iguais + força (PasswordValidatorUtil),
     * marca o token como usado, revoga sessões e zera o lockout.
     */
    @Transactional
    public void redefinirSenha(String token, String novaSenha, String confirmacaoSenha) {
        if (!novaSenha.equals(confirmacaoSenha)) {
            throw new BusinessException("Senhas não conferem", HttpStatus.BAD_REQUEST);
        }

        PasswordResetToken prt = tokenRepository.findByTokenHash(sha256Hex(token))
                .orElseThrow(() -> new BusinessException("Token inválido ou expirado", HttpStatus.UNPROCESSABLE_ENTITY));

        if (prt.getUsedAt() != null) {
            throw new BusinessException("Token inválido ou expirado", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (prt.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("Token inválido ou expirado", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        UserAccount user = prt.getUser();
        String tenantName = user.getTenant() != null ? user.getTenant().getName() : null;
        passwordValidatorUtil.validatePassword(novaSenha, tenantName);

        user.setPasswordHash(passwordEncoder.encode(novaSenha));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastUpdateDate(Instant.now());
        user.setLastUpdatedBy("password-reset");
        userRepo.save(user);

        prt.setUsedAt(Instant.now());
        tokenRepository.save(prt);

        // Invalida todas as sessões ativas — quem trocou a senha (ou um atacante) é deslogado.
        refreshTokenRepository.revokeAllActiveByUserId(user.getId(), Instant.now());

        auditService.logAuditEventWithActor("PASSWORD_RESET_COMPLETED", user.getId(), Constants.USER,
                user.getId(), Constants.SUCCESS, "Senha redefinida via token", null);

        logger.info("Senha redefinida para o usuário {}", user.getId());
    }

    private void publishResetEmail(String email, String name, String rawToken) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "email", email,
                    "name", name != null ? name : email.split("@")[0],
                    "token", rawToken,
                    "type", "RESET_SENHA"));
            kafkaTemplate.send(EmailNotificationService.EMAIL_TOPIC, email, payload);
        } catch (Exception e) {
            logger.error("Falha ao publicar e-mail RESET_SENHA para {}", email, e);
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar hash do token de reset", e);
        }
    }
}