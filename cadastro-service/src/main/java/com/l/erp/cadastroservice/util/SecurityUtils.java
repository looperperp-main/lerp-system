package com.l.erp.cadastroservice.util;

import com.l.erp.cadastroservice.api.dto.CurrentUser;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<UUID> getCurrentUserId() {
        return getHeader(Constants.HEADER_USER_ID).map(UUID::fromString);
    }

    public static Optional<Long> getCurrentTenantId() {
        return getHeader(Constants.HEADER_TENANT_ID).map(Long::valueOf);
    }

    public static Optional<String> getCurrentUserEmail() {
        return getHeader(Constants.HEADER_USER_EMAIL);
    }

    public static CurrentUser getCurrentUserInfo() {
        UUID userId = getCurrentUserId().orElseThrow(() -> new BusinessException(Constants.USUARIO_NAO_AUTENTICADO, HttpStatus.UNAUTHORIZED));
        String email = getCurrentUserEmail().orElse("user-" + userId);
        return new CurrentUser(userId, email);
    }

    public static UUID getCorrelationIdFromRequest(Logger logger) {
        HttpServletRequest request = getRequest();
        if (request != null) {
            String headerCorId = request.getHeader("X-Correlation-ID");
            if (headerCorId != null && !headerCorId.isBlank()) {
                try {
                    return UUID.fromString(headerCorId);
                } catch (IllegalArgumentException _) {
                    logger.warn("Formato de Correlation ID inválido recebido no header: {}", headerCorId);
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
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
