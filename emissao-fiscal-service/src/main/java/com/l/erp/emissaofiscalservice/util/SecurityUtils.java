package com.l.erp.emissaofiscalservice.util;

import com.l.erp.common.util.Constants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/** Mesmo padrão do cadastro-service/operacoes-service: quem barra e valida o JWT é o gateway;
 *  este serviço só lê os headers (X-Tenant-Id etc.) que o gateway já injetou. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<Long> getCurrentTenantId() {
        return getHeader(Constants.HEADER_TENANT_ID).map(Long::valueOf);
    }

    public static Optional<String> getCurrentUserId() {
        return getHeader(Constants.HEADER_USER_ID);
    }

    /** Authority populada pelo InternalRequestFilter a partir do header X-Authorities do gateway. */
    public static boolean hasAuthority(String authority) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
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
