package com.l.erp.billingservice.util;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<String> getCurrentUserSub() {
        return getHeader("X-User-Id");
    }

    public static Optional<UUID> getCurrentUserId() {
        return getHeader("X-User-Id").map(UUID::fromString);
    }

    public static UUID getCorrelationIdFromRequest(Logger log) {
        HttpServletRequest request = getRequest();
        if (request != null) {
            String headerCorId = request.getHeader("X-Correlation-ID");
            if (headerCorId != null && !headerCorId.isBlank()) {
                try {
                    return UUID.fromString(headerCorId);
                } catch (IllegalArgumentException _) {
                    log.warn("Formato de Correlation ID inválido: {}", headerCorId);
                }
            }
        }
        return UUID.randomUUID();
    }

    private static Optional<String> getHeader(String name) {
        HttpServletRequest request = getRequest();
        if (request == null) return Optional.empty();
        String value = request.getHeader(name);
        return (value != null && !value.isBlank()) ? Optional.of(value) : Optional.empty();
    }

    private static HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }
}