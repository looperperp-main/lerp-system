package com.l.erp.authservice.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SecurityUtils {
    private SecurityUtils(){

    }

    public static Optional<UUID> getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(auth.getPrincipal().toString()));
    }

    public static Optional<String> getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getDetails() == null) {
            return Optional.empty();
        }
        if (auth.getDetails() instanceof Map<?, ?> details) {
            Object email = details.get("email");
            return email == null ? Optional.empty() : Optional.of(email.toString());
        }
        return Optional.empty();
    }
}
