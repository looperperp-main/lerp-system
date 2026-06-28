package com.l.erp.authservice.infra;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.l.erp.authservice.api.dto.AtivarContaRequest;
import com.l.erp.authservice.api.dto.CriarContaGratisRequest;
import com.l.erp.authservice.api.dto.LoginResponse;
import com.l.erp.authservice.api.dto.RefreshResponse;
import com.l.erp.authservice.api.dto.TenantLoginResponse;
import com.l.erp.authservice.dominio.enumerators.EnumTenantStatus;
import com.l.erp.authservice.api.mappers.AuthMapper;
import com.l.erp.authservice.dominio.RefreshToken;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.infra.config.Roles;
import com.l.erp.authservice.repositorios.OwnerMarkerRepository;
import com.l.erp.authservice.repositorios.RolePermissionRepository;
import com.l.erp.authservice.repositorios.TenantRepository;
import com.l.erp.authservice.repositorios.UserAccountRepository;
import com.l.erp.authservice.repositorios.UserRoleRepository;
import com.l.erp.authservice.services.audit.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.authservice.util.PasswordValidatorUtil;
import com.l.erp.common.util.Constants;
import org.springframework.kafka.core.KafkaTemplate;
import com.l.erp.common.exception.custom.UserLockedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {

    private final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final UserAccountRepository userRepo;
    private final OwnerMarkerRepository ownerRepo;
    private final TenantRepository tenantRepository;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuthMapper authMapper;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AuditService auditService;
    private final PasswordValidatorUtil passwordValidatorUtil;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final com.l.erp.authservice.services.TrialEngagementService trialEngagementService;

    public AuthService(UserAccountRepository userRepo,
                       OwnerMarkerRepository ownerRepo,
                       TenantRepository tenantRepository,
                       TokenService tokenService,
                       RefreshTokenService refreshTokenService,
                       PasswordEncoder passwordEncoder,
                       AuthMapper authMapper,
                       UserRoleRepository userRoleRepository,
                       RolePermissionRepository rolePermissionRepository,
                       AuditService auditService,
                       PasswordValidatorUtil passwordValidatorUtil,
                       KafkaTemplate<String, String> kafkaTemplate,
                       ObjectMapper objectMapper,
                       com.l.erp.authservice.services.TrialEngagementService trialEngagementService) {
        this.userRepo = userRepo;
        this.ownerRepo = ownerRepo;
        this.tenantRepository = tenantRepository;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.authMapper = authMapper;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.auditService = auditService;
        this.passwordValidatorUtil = passwordValidatorUtil;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.trialEngagementService = trialEngagementService;
    }

    public LoginResponse loginPartner(String email, String password) {
        UserAccount user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, Constants.USER_EMAIL_NOT_CORRECT));

        if (!Constants.PARTNER.equals(user.getUserType())) {
            throw new ResponseStatusException(UNAUTHORIZED, Constants.USER_EMAIL_NOT_CORRECT);
        }

        if (!user.isActive()) {
            auditService.logAuditEventWithActor(Constants.LOGIN_USER_INACTIVE, user.getId(), Constants.USER, user.getId(),
                    Constants.FAILED, "Tentativa de login em conta de parceiro inativa", null);
            throw new ResponseStatusException(UNAUTHORIZED, Constants.USER_INACTIVE);
        }

        if (isUserLocked(user)) {
            auditService.logAuditEventWithActor(Constants.LOGIN_LOCKED, user.getId(), Constants.USER, user.getId(),
                    Constants.FAILED, "Tentativa de login em conta de parceiro bloqueada", null);
            throw new UserLockedException(user.getLockedUntil());
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            handleFailedLogin(user);
            auditService.logAuditEventWithActor(Constants.PARTNER_LOGIN_FAILED, user.getId(), Constants.USER, user.getId(),
                    Constants.FAILED, "Senha incorreta - Tentativa " + user.getFailedLoginAttempts(), null);
            throw new ResponseStatusException(UNAUTHORIZED, Constants.USER_EMAIL_NOT_CORRECT);
        }

        resetFailedAttempts(user);

        auditService.logAuditEventWithActor(Constants.PARTNER_LOGIN_SUCCESS, user.getId(), Constants.USER, user.getId(),
                Constants.SUCCESS, null, null);

        String jwt = tokenService.generatePartnerToken(user);
        RefreshTokenService.TokenPair tokenPair = refreshTokenService.issue(user, null);

        return authMapper.toLoginResponse(user, jwt, tokenPair.rawToken());
    }

    public LoginResponse login(String email, String password){
        UserAccount user = userRepo.findByEmail(email).
                orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED,Constants.USER_EMAIL_NOT_CORRECT));

        if (Constants.PARTNER.equals(user.getUserType())) {
            throw new ResponseStatusException(UNAUTHORIZED, Constants.USER_EMAIL_NOT_CORRECT);
        }

        if(!user.isActive()){
            auditService.logAuditEventWithActor(Constants.LOGIN_USER_INACTIVE, user.getId(), Constants.USER, user.getId(),
                    Constants.FAILED, "Tentativa de login em conta inativa", null);
            throw  new ResponseStatusException(UNAUTHORIZED, Constants.USER_INACTIVE);
        }

        // Verifica se o usuário está bloqueado
        if (isUserLocked(user)) {
            auditService.logAuditEventWithActor(Constants.LOGIN_LOCKED, user.getId(), Constants.USER, user.getId(),
                    Constants.FAILED, "Tentativa de login em conta bloqueada", null);
            throw new UserLockedException(user.getLockedUntil());
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            handleFailedLogin(user);
            auditService.logAuditEventWithActor(Constants.LOGIN_FAILED, user.getId(), Constants.USER, user.getId(),
                    Constants.FAILED, "Senha incorreta - Tentativa " + user.getFailedLoginAttempts(), null);
            throw new ResponseStatusException(UNAUTHORIZED, Constants.USER_EMAIL_NOT_CORRECT);
        }

        // Login bem-sucedido: reseta as tentativas falhas
        resetFailedAttempts(user);

        // Registra login bem-sucedido na auditoria
        auditService.logAuditEventWithActor(Constants.LOGIN_SUCCESS, user.getId(), Constants.USER, user.getId(),
                Constants.SUCCESS, null, null);

        boolean isOwner = ownerRepo.existsByUser_IdAndEnabledTrue(user.getId());
        List<String> roles = isOwner ? List.of(Roles.APP_OWNER,Roles.TENANT_OWNER) : List.of("ROLE_USER");//TODO: get roles from database

        String jwt = tokenService.generateToken(user, roles, isOwner, getPermissions(user.getId()));

        RefreshTokenService.TokenPair tokenPair = refreshTokenService.issue(user, null);

        return authMapper.toLoginResponse(user, jwt, tokenPair.rawToken());

    }

    /**
     * Login para usuários de tenant (sistema principal)
     * Requer CNPJ do tenant + e-mail + senha
     */
    public TenantLoginResponse loginWithTenant(String cnpj, String email, String password) {
        logger.debug("Tentativa de login de tenant - CNPJ: {}, Email: {}", cnpj, email);

        // 1. Buscar e validar o Tenant pelo CNPJ
        Tenant tenant = tenantRepository.findByCnpj(cnpj)
                .orElseThrow(() -> {
                    logger.warn("Tentativa de login com CNPJ inexistente: {}", cnpj);
                    return new ResponseStatusException(UNAUTHORIZED, Constants.TENANT_CNPJ_NOT_FOUND);
                });

        // 2. Verificar se o tenant está ativo
        if (!Constants.ATIVO.equalsIgnoreCase(tenant.getStatus().getDescription()) && !Constants.TRIAL.equalsIgnoreCase(tenant.getStatus().getDescription())) {
            logger.warn("Tentativa de login em tenant inativo - CNPJ: {}, Status: {}", cnpj, tenant.getStatus());
            throw new ResponseStatusException(UNAUTHORIZED, Constants.TENANT_NOT_ACTIVE);
        }

        // 3. Buscar usuário pelo e-mail E tenant
        UserAccount user = userRepo.findByEmailAndTenantId(email, tenant.getId())
                .orElseThrow(() -> {
                    logger.warn("Usuário não encontrado para email {} no tenant {}", email, tenant.getId());
                    return new ResponseStatusException(UNAUTHORIZED, Constants.USER_EMAIL_NOT_CORRECT);
                });

        // 4. Validar se o usuário está ativo
        if (!user.isActive()) {
            auditService.logAuditEventWithActor(Constants.LOGIN_USER_INACTIVE, user.getId(), Constants.USER, user.getId(),
                    Constants.FAILED, "Tentativa de login em conta inativa - Tenant: " + tenant.getName(), null);
            throw new ResponseStatusException(UNAUTHORIZED, Constants.USER_INACTIVE);
        }

        // 5. Verificar bloqueio
        if (isUserLocked(user)) {
            auditService.logAuditEventWithActor(Constants.LOGIN_LOCKED, user.getId(), Constants.USER, user.getId(),
                    Constants.FAILED, "Tentativa de login em conta bloqueada - Tenant: " + tenant.getName(), null);
            throw new UserLockedException(user.getLockedUntil());
        }

        // 6. Validar senha
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            handleFailedLogin(user);
            auditService.logAuditEventWithActor(Constants.TENANT_LOGIN_FAILED, user.getId(), Constants.USER, user.getId(),
                    Constants.FAILED, "Senha ou email incorretos - Tentativa " + user.getFailedLoginAttempts() + " - Tenant: " + tenant.getName(), null);
            throw new ResponseStatusException(UNAUTHORIZED, Constants.USER_EMAIL_NOT_CORRECT);
        }

        // 7. Login bem-sucedido
        resetFailedAttempts(user);

        auditService.logAuditEventWithActor(Constants.TENANT_LOGIN_SUCCESS, user.getId(), Constants.USER, user.getId(),
                Constants.SUCCESS, "Login no tenant: " + tenant.getName(), null);

        if (EnumTenantStatus.TRIAL.equals(tenant.getStatus())) {
            trialEngagementService.trackLogin(tenant.getId());
        }
        publishTrialLoginEvent(tenant.getId());

        // 8. Buscar permissões do usuário
        List<String> permissions = getPermissions(user.getId());

        // 9. Gerar JWT específico para tenant
        String jwt = tokenService.generateTenantUserToken(user, permissions, tenant);

        // 10. Gerar refresh token
        RefreshTokenService.TokenPair tokenPair = refreshTokenService.issue(user, null);

        logger.info("Login de tenant bem-sucedido - Usuário: {}, Tenant: {}", user.getEmail(), tenant.getName());

        return authMapper.toTenantLoginResponse(user, jwt, tokenPair.rawToken(), tenant);
    }

    @Transactional
    public RefreshResponse refresh(String refreshTokenRaw) {
        RefreshToken existingRT = refreshTokenService.findByHash(refreshTokenRaw)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Refresh Token inválido"));

        if (existingRT.getRevokedAt() != null) {
            auditService.logAuditEventWithActor(
                    Constants.TOKEN_REFRESH_REUSE,
                    existingRT.getUser().getId(), Constants.USER,
                    existingRT.getUser().getId(), Constants.FAILED,
                    "Refresh token revogado reutilizado - possível comprometimento de sessão", null);
            refreshTokenService.revokeFamily(existingRT.getFamilyId());
            throw new ResponseStatusException(UNAUTHORIZED, "Sessão comprometida. Faça login novamente.");
        }

        if (existingRT.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenService.revoke(existingRT, null);
            throw new ResponseStatusException(UNAUTHORIZED, "Refresh Token expirado");
        }

        UserAccount user = existingRT.getUser();
        String newJwt = generateJwtForUser(user);

        RefreshTokenService.TokenPair newRt = refreshTokenService.issue(user, existingRT.getFamilyId());
        refreshTokenService.revoke(existingRT, newRt.entity());

        auditService.logAuditEventWithActor(Constants.TOKEN_REFRESH_SUCCESS, user.getId(), Constants.USER,
                user.getId(), Constants.SUCCESS, null, null);

        return new RefreshResponse(newJwt, newRt.rawToken());
    }

    private String generateJwtForUser(UserAccount user) {
        if (Constants.PARTNER.equals(user.getUserType())) {
            return tokenService.generatePartnerToken(user);
        }
        if (Constants.TENANT_USER.equals(user.getUserType())) {
            List<String> permissions = getPermissions(user.getId());
            return tokenService.generateTenantUserToken(user, permissions, user.getTenant());
        }
        boolean isOwner = ownerRepo.existsByUser_IdAndEnabledTrue(user.getId());
        List<String> roles = isOwner ? List.of(Roles.APP_OWNER, Roles.TENANT_OWNER) : List.of("ROLE_USER");
        return tokenService.generateToken(user, roles, isOwner, getPermissions(user.getId()));
    }

    public void logout(String refreshTokenRaw) {
        refreshTokenService.findValid(refreshTokenRaw)
                .ifPresent(rt -> {
                    UUID userId = rt.getUser().getId();
                    auditService.logAuditEventWithActor(Constants.LOGOUT, userId, Constants.USER, userId,
                            Constants.SUCCESS, null, null);
                    refreshTokenService.revoke(rt, null);
                });
    }

    private List<String> getPermissions(UUID userId){
        return userRoleRepository.findAllByUserId(userId).stream()
                .flatMap(ur -> rolePermissionRepository.findAllByRoleId(ur.getRole().getId()).stream())
                .map(rp -> rp.getPermission().getCode()) // Pega o campo "TENANT_INSERT", "TENANT_UPDATE"
                .distinct()
                .toList();
    }

    private boolean isUserLocked(UserAccount user) {
        if (user.getLockedUntil() == null) {
            return false;
        }
        // Se o tempo de bloqueio já passou, desbloqueia automaticamente
        if (Instant.now().isAfter(user.getLockedUntil())) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
            userRepo.save(user);
            // Registra desbloqueio automático
            auditService.logAuditEventWithActor(Constants.USER_UNLOCKED, user.getId(), Constants.USER, user.getId(),
                    Constants.SUCCESS, "Desbloqueio automático após expiração do tempo", null);
            return false;
        }
        return true;
    }

    /**
     * Trata uma tentativa de login falha
     */
    private void handleFailedLogin(UserAccount user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= Constants.MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(Constants.LOCK_DURATION));
        }

        user.setLastUpdateDate(Instant.now());
        user.setLastUpdatedBy(Constants.SYSTEM);
        userRepo.save(user);
    }

    /**
     * Reseta as tentativas falhas após um login bem-sucedido
     */
    private void resetFailedAttempts(UserAccount user) {
        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepo.save(user);
        }
    }

    @Transactional
    public void ativarConta(AtivarContaRequest req) {
        if (!req.senha().equals(req.confirmacaoSenha())) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Senhas não conferem");
        }

        DecodedJWT decoded;
        try {
            decoded = tokenService.validateInvitationToken(req.token());
        } catch (RuntimeException error) {
            logger.error("Error: ", error);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Token inválido ou expirado");
        }

        Long tenantId = Long.valueOf(decoded.getSubject());
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Empresa não encontrada"));

        if (tenant.getStatus() != EnumTenantStatus.CONVIDADO) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Conta já ativada");
        }

        if (userRepo.findByEmailAndTenantId(tenant.getEmail(), tenantId).isPresent()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Conta já ativada");
        }

        // email é único global em users_account — colisão com outro tenant/parceiro precisa
        // de mensagem clara antes do insert (senão estoura constraint e dá rollback).
        if (userRepo.findByEmail(tenant.getEmail()).isPresent()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Este e-mail já está em uso em outra conta.");
        }

        String emailDecoded = decoded.getClaim(Constants.email).asString();
        String displayName = emailDecoded != null
                ? emailDecoded.split("@")[0]
                : tenant.getEmail().split("@")[0];

        passwordValidatorUtil.validatePassword(req.senha(), tenant.getName());

        UserAccount user = new UserAccount();
        user.setTenant(tenant);
        user.setEmail(tenant.getEmail());
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(req.senha()));
        user.setUserType("TENANT_OWNER");
        user.setActive(true);
        user.setFailedLoginAttempts(0);
        user.setCreatedDate(Instant.now());
        user.setCreatedBy("self-activation");
        userRepo.save(user);

        Instant trialStartedAt = Instant.now();
        Instant trialExpiresAt = trialStartedAt.plus(15, ChronoUnit.DAYS);

        tenant.setStatus(EnumTenantStatus.TRIAL);
        tenant.setTrialStartedAt(trialStartedAt);
        tenant.setTrialExpiresAt(trialExpiresAt);
        tenantRepository.save(tenant);

        String referralId = decoded.getClaim("referralId").asString();
        publishTenantActivated(tenant.getId(), referralId, trialStartedAt, trialExpiresAt);

        logger.info("Conta ativada (TRIAL) para tenant {} ({}), trial expira em {}", tenant.getId(), tenant.getEmail(), trialExpiresAt);
    }

    private void publishTrialLoginEvent(Long tenantId) {
        try {
            java.util.Map<String, Object> event = new java.util.HashMap<>();
            event.put("tenantId", tenantId);
            kafkaTemplate.send("trial.login", String.valueOf(tenantId), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            logger.error("Falha ao publicar trial.login para tenant {}", tenantId, e);
        }
    }

    @Transactional
    public Optional<TenantLoginResponse> criarContaGratis(CriarContaGratisRequest req) {
        String cnpjDigits = req.cnpj().replaceAll("\\D", "");

        if (tenantRepository.findByCnpj(cnpjDigits).isPresent()) {
            publishJaExisteConta(req.email());
            return Optional.empty();
        }
        if (userRepo.findByEmail(req.email()).isPresent()) {
            publishJaExisteConta(req.email());
            return Optional.empty();
        }

        passwordValidatorUtil.validatePassword(req.senha(), req.razaoSocial());

        Instant now = Instant.now();
        Instant trialExpiresAt = now.plus(15, ChronoUnit.DAYS);

        Tenant tenant = new Tenant();
        tenant.setName(req.razaoSocial());
        tenant.setCnpj(cnpjDigits);
        tenant.setNomeFantasia(req.nomeFantasia());
        tenant.setEmail(req.email());
        tenant.setTelefone(req.telefone());
        tenant.setStatus(EnumTenantStatus.TRIAL);
        tenant.setCreationDate(now);
        tenant.setCreatedBy("self-registration");
        tenant.setTrialStartedAt(now);
        tenant.setTrialExpiresAt(trialExpiresAt);
        tenant = tenantRepository.save(tenant);

        String displayName = req.email().split("@")[0];
        UserAccount user = new UserAccount();
        user.setTenant(tenant);
        user.setEmail(req.email());
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(req.senha()));
        user.setUserType(Constants.TENANT_USER);
        user.setActive(true);
        user.setFailedLoginAttempts(0);
        user.setCreatedDate(now);
        user.setCreatedBy("self-registration");
        userRepo.save(user);

        publishBoasVindasTrial(req.email(), displayName, tenant.getName(), trialExpiresAt);

        auditService.logAuditEventWithActor("CRIAR_CONTA_GRATIS", user.getId(), Constants.USER,
                user.getId(), Constants.SUCCESS, "Auto-cadastro para " + tenant.getName(), null);

        logger.info("Conta grátis criada (TRIAL) para tenant {} ({}), trial expira em {}",
                tenant.getId(), req.email(), trialExpiresAt);

        List<String> permissions = getPermissions(user.getId());
        String jwt = tokenService.generateTenantUserToken(user, permissions, tenant);
        RefreshTokenService.TokenPair tokenPair = refreshTokenService.issue(user, null);

        return Optional.of(authMapper.toTenantLoginResponse(user, jwt, tokenPair.rawToken(), tenant));
    }

    private void publishJaExisteConta(String email) {
        try {
            java.util.Map<String, Object> event = new java.util.HashMap<>();
            event.put(Constants.email, email);
            event.put("type", "JA_EXISTE_CONTA");
            kafkaTemplate.send("user-welcome-email-topic", email, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            logger.error("Falha ao publicar e-mail JA_EXISTE_CONTA para {}", email, e);
        }
    }

    private void publishBoasVindasTrial(String email, String name, String tenantName, Instant trialExpiresAt) {
        try {
            java.util.Map<String, Object> event = new java.util.HashMap<>();
            event.put(Constants.email, email);
            event.put("name", name);
            event.put("tenantName", tenantName);
            event.put("trialExpiresAt", trialExpiresAt.toString());
            event.put("type", "BOAS_VINDAS_TRIAL");
            kafkaTemplate.send("user-welcome-email-topic", email, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            logger.error("Falha ao publicar e-mail de boas-vindas trial para {}", email, e);
        }
    }

    private void publishTenantActivated(Long tenantId, String referralId, Instant trialStartedAt, Instant trialExpiresAt) {
        try {
            java.util.Map<String, Object> event = new java.util.HashMap<>();
            event.put("tenantId", tenantId);
            event.put("referralId", referralId);
            event.put("trialStartedAt", trialStartedAt.toString());
            event.put("trialExpiresAt", trialExpiresAt.toString());
            kafkaTemplate.send("partner.tenant.activated", referralId, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            logger.error("Falha ao publicar partner.tenant.activated para tenant {}", tenantId, e);
        }
    }
}
