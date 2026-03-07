package com.l.erp.authservice.infra;

import com.l.erp.authservice.api.dto.LoginResponse;
import com.l.erp.authservice.api.dto.RefreshResponse;
import com.l.erp.authservice.services.audit.AuditService;
import com.l.erp.common.exception.custom.UserLockedException;
import com.l.erp.authservice.api.mappers.AuthMapper;
import com.l.erp.authservice.dominio.RefreshToken;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.infra.config.Roles;
import com.l.erp.authservice.repositorios.OwnerMarkerRepository;
import com.l.erp.authservice.repositorios.RolePermissionRepository;
import com.l.erp.authservice.repositorios.UserAccountRepository;
import com.l.erp.authservice.repositorios.UserRoleRepository;
import com.l.erp.authservice.util.Constants;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {
    private final UserAccountRepository userRepo;
    private final OwnerMarkerRepository ownerRepo;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuthMapper authMapper;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AuditService auditService;

    public AuthService(UserAccountRepository userRepo,
                       OwnerMarkerRepository ownerRepo,
                       TokenService tokenService,
                       RefreshTokenService refreshTokenService,
                       PasswordEncoder passwordEncoder,
                       AuthMapper authMapper,
                       UserRoleRepository userRoleRepository,
                       RolePermissionRepository rolePermissionRepository,
                       AuditService auditService) {
        this.userRepo = userRepo;
        this.ownerRepo = ownerRepo;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.authMapper = authMapper;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.auditService = auditService;
    }

    public LoginResponse login(String email, String password){
        UserAccount user = userRepo.findByEmail(email).
                orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED,Constants.USER_EMAIL_NOT_CORRECT));

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

        String jwt = tokenService.generateToken(user, roles, isOwner, getRoles(user.getId()));

        RefreshTokenService.TokenPair tokenPair = refreshTokenService.issue(user);

        return authMapper.toLoginResponse(user, jwt, tokenPair.rawToken());

    }

    public RefreshResponse refresh(String refreshTokenRaw){
        RefreshToken oldRT = refreshTokenService.findValid(refreshTokenRaw)
                .orElseThrow(() -> new RuntimeException("Refresh Token inválido"));

        UserAccount user = oldRT.getUser();

        boolean isOwner = ownerRepo.existsByUser_IdAndEnabledTrue(user.getId());
        List<String> roles = isOwner ? List.of("ROLE_OWNER", "ROLE_USER") : List.of("ROLE_USER");

        String newJwt = tokenService.generateToken(user, roles, isOwner, getRoles(user.getId()));

        RefreshTokenService.TokenPair newRt = refreshTokenService.issue(user);
        refreshTokenService.revoke(oldRT, newRt.entity());

        return new RefreshResponse(newJwt, newRt.rawToken());
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

    private List<String> getRoles(UUID userId){
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
