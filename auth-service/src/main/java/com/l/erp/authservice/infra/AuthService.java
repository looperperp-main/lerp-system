package com.l.erp.authservice.infra;

import com.l.erp.authservice.api.dto.LoginResponse;
import com.l.erp.authservice.api.dto.RefreshResponse;
import com.l.erp.authservice.api.mappers.AuthMapper;
import com.l.erp.authservice.dominio.RefreshToken;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.infra.config.Roles;
import com.l.erp.authservice.repositorios.OwnerMarkerRepository;
import com.l.erp.authservice.repositorios.RolePermissionRepository;
import com.l.erp.authservice.repositorios.UserAccountRepository;
import com.l.erp.authservice.repositorios.UserRoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    public AuthService(UserAccountRepository userRepo,
                       OwnerMarkerRepository ownerRepo,
                       TokenService tokenService,
                       RefreshTokenService refreshTokenService,
                       PasswordEncoder passwordEncoder,
                       AuthMapper authMapper,
                       UserRoleRepository userRoleRepository,
                       RolePermissionRepository rolePermissionRepository) {
        this.userRepo = userRepo;
        this.ownerRepo = ownerRepo;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.authMapper = authMapper;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    public LoginResponse login(String email, String password){
        UserAccount user = userRepo.findByEmail(email).
                orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED,"Credenciais Inválidas - Email incorreto"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Credenciais Inválidas - Senha Incorreta");
        }

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
                .ifPresent(rt -> refreshTokenService.revoke(rt, null));
    }

    private List<String> getRoles(UUID userId){
        return userRoleRepository.findAllByUserId(userId).stream()
                .flatMap(ur -> rolePermissionRepository.findAllByRoleId(ur.getRole().getId()).stream())
                .map(rp -> rp.getPermission().getCode()) // Pega o campo "TENANT_INSERT", "TENANT_UPDATE"
                .distinct()
                .toList();
    }
}
