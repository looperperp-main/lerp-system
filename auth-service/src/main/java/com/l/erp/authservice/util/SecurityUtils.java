package com.l.erp.authservice.util;

import com.l.erp.authservice.api.dto.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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

    public static CurrentUser getCurrentUserInfo() {
        UUID userId = getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USUARIO_NAO_AUTENTICADO));
        String email = getCurrentUserEmail().orElseThrow(() -> new RuntimeException(Constants.USUARIO_UUID_NAO_ENCONTRADO));
        return new CurrentUser(userId, email);
    }

    /**
     * Métod auxiliar para extrair o Correlation ID do Header da Requisição atual.
     */
    public static UUID getCorrelationIdFromRequest(Logger logger) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            // Substitua "X-Correlation-ID" pelo nome do header que o seu Gateway ou Front-end está enviando
            String headerCorId = request.getHeader("X-Correlation-ID");

            if (headerCorId != null && !headerCorId.isBlank()) {
                try {
                    return UUID.fromString(headerCorId);
                } catch (IllegalArgumentException e) {
                    logger.warn("Formato de Correlation ID inválido recebido no header: {}", headerCorId);
                }
            }
        }
        // Se não houver contexto web (ex: rotina assíncrona/batch) ou o header não foi enviado,
        // gera um novo para não deixar a auditoria vazia.
        return UUID.randomUUID();
    }
}
