package com.l.erp.authservice.infra;

import com.l.erp.authservice.dominio.RefreshToken;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.repositorios.RefreshTokenRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public TokenPair issue(UserAccount user) {
        String rawToken = UUID.randomUUID().toString();
        long tokenHash = hashToLong(rawToken);

        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(tokenHash);
        rt.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        refreshTokenRepository.save(rt);

        return new TokenPair(rawToken, rt);
    }

    public Optional<RefreshToken> findValid(String rawToken) {
        long tokenHash = hashToLong(rawToken);
        return refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHash)
                .filter(rt -> rt.getExpiresAt().isAfter(Instant.now()));
    }

    public void revoke(RefreshToken token, RefreshToken replacedBy) {
        token.setRevokedAt(Instant.now());
        if (replacedBy != null) {
            token.setReplacedByTokenId(replacedBy.getId());
        }
        refreshTokenRepository.save(token);
    }

    private long hashToLong(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
            return Long.parseUnsignedLong(HexFormat.of().formatHex(digest).substring(0, 16), 16);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao hash do refresh token");
        }
    }

    public record TokenPair(String rawToken, RefreshToken entity) {}
}
