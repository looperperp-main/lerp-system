package com.l.erp.migracaoservice.util;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

/** Lê os headers que o gateway injeta (tenant Long, usuário UUID). Sem ThreadLocal: o tenant é
 *  sempre lido explicitamente aqui e passado adiante, porque o trabalho @Async roda fora do request. */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<UUID> getCurrentUserId() {
        return getHeader(Constants.HEADER_USER_ID).map(UUID::fromString);
    }

    public static Optional<Long> getCurrentTenantId() {
        return getHeader(Constants.HEADER_TENANT_ID).map(Long::valueOf);
    }

    public static UUID requireUserId() {
        return getCurrentUserId().orElseThrow(
                () -> new BusinessException(Constants.USUARIO_NAO_AUTENTICADO, HttpStatus.UNAUTHORIZED));
    }

    public static Long requireTenantId() {
        return getCurrentTenantId().orElseThrow(
                () -> new BusinessException(Constants.USUARIO_NAO_AUTENTICADO, HttpStatus.UNAUTHORIZED));
    }

    /** Authority populada pelo InternalRequestFilter a partir do header X-Authorities do gateway. */
    public static boolean hasAuthority(String authority) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }

    private static Optional<String> getHeader(String name) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return Optional.empty();
        String value = attributes.getRequest().getHeader(name);
        return (value != null && !value.isBlank()) ? Optional.of(value) : Optional.empty();
    }
}
