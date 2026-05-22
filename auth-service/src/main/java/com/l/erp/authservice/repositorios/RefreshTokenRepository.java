package com.l.erp.authservice.repositorios;

import com.l.erp.authservice.dominio.RefreshToken;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHashAndRevokedAtIsNull(@NotNull String tokenHash);

    Optional<RefreshToken> findByTokenHash(@NotNull String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now WHERE rt.familyId = :familyId AND rt.revokedAt IS NULL")
    void revokeAllActiveByFamilyId(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :cutoff OR rt.revokedAt IS NOT NULL")
    int deleteExpiredAndRevoked(@Param("cutoff") Instant cutoff);
}
