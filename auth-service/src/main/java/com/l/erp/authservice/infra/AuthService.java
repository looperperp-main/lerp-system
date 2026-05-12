package com.l.erp.authservice.infra;

import com.l.erp.authservice.api.dto.LoginResponse;
import com.l.erp.authservice.api.dto.RefreshResponse;
import com.l.erp.authservice.api.dto.TenantLoginResponse;
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
import com.l.erp.authservice.util.Constants;
import com.l.erp.common.exception.custom.UserLockedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
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

    public AuthService(UserAccountRepository userRepo,
                       OwnerMarkerRepository ownerRepo,
                       TenantRepository tenantRepository,
                       TokenService tokenService,
                       RefreshTokenService refreshTokenService,
                       PasswordEncoder passwordEncoder,
                       AuthMapper authMapper,
                       UserRoleRepository userRoleRepository,
                       RolePermissionRepository rolePermissionRepository,
                       AuditService auditService) {
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
        if (!Constants.ATIVO.equalsIgnoreCase(tenant.getStatus().getDescription())) {
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
}
