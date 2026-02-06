package com.l.erp.authservice.infra;

import com.l.erp.authservice.api.dto.LoginResponse;
import com.l.erp.authservice.api.dto.RefreshResponse;
import com.l.erp.authservice.dominio.RefreshToken;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.repositorios.OwnerMarkerRepository;
import com.l.erp.authservice.repositorios.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthService {
    private final UserAccountRepository userRepo;
    private final OwnerMarkerRepository ownerRepo;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserAccountRepository userRepo,
                       OwnerMarkerRepository ownerRepo,
                       TokenService tokenService,
                       RefreshTokenService refreshTokenService,
                       PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.ownerRepo = ownerRepo;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(String email, String password){
        UserAccount user = userRepo.findByEmail(email).
                orElseThrow(() -> new RuntimeException("Credenciais Inválidas"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("Credenciais Inválidas");
        }

        boolean isOwner = ownerRepo.existsByUser_IdAndEnabledTrue(user.getId());
        List<String> roles = isOwner ? List.of("ROLE_OWNER","ROLE_USER") : List.of("ROLE_USER");

        String jwt = tokenService.generateToken(user, roles, isOwner);

        RefreshTokenService.TokenPair tokenPair = refreshTokenService.issue(user);

        return new LoginResponse(user.getDisplayName(), jwt, tokenPair.rawToken());

    }

    public RefreshResponse refresh(String refreshTokenRaw){
        RefreshToken oldRT = refreshTokenService.findValid(refreshTokenRaw)
                .orElseThrow(() -> new RuntimeException("Refresh Token inválido"));

        UserAccount user = oldRT.getUser();

        boolean isOwner = ownerRepo.existsByUser_IdAndEnabledTrue(user.getId());
        List<String> roles = isOwner ? List.of("ROLE_OWNER", "ROLE_USER") : List.of("ROLE_USER");

        String newJwt = tokenService.generateToken(user, roles, isOwner);

        RefreshTokenService.TokenPair newRt = refreshTokenService.issue(user);
        refreshTokenService.revoke(oldRT, newRt.entity());

        return new RefreshResponse(newJwt, newRt.rawToken());
    }

    public void logout(String refreshTokenRaw) {
        refreshTokenService.findValid(refreshTokenRaw)
                .ifPresent(rt -> refreshTokenService.revoke(rt, null));
    }
}
