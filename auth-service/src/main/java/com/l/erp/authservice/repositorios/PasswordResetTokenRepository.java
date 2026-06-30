package com.l.erp.authservice.repositorios;

import com.l.erp.authservice.dominio.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // Token mais recente do usuário — usado no throttle (cooldown entre solicitações).
    Optional<PasswordResetToken> findFirstByUser_IdOrderByCreatedAtDesc(UUID userId);
}