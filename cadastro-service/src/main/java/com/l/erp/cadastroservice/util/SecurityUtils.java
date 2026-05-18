package com.l.erp.cadastroservice.util;

import com.l.erp.cadastroservice.api.dto.CurrentUser;
import com.l.erp.common.util.Constants;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

public final class SecurityUtils {

    private static final Logger logger = LoggerFactory.getLogger(SecurityUtils.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private SecurityUtils() {}

    /**
     * Extrai um claim (campo) específico do Token JWT recebido no Header Authorization.
     */
    private static Optional<String> getClaimFromJwt(String claimKey) {
        HttpServletRequest request = getRequest();
        if (request != null) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String token = authHeader.substring(7);
                    String[] parts = token.split("\\.");
                    Optional<String> jsonNode = getClaimValue(claimKey, parts);
                    if (jsonNode.isPresent()) return jsonNode;
                } catch (Exception e) {
                    logger.error("Erro ao ler o JWT no SecurityUtils", e);
                }
            } else {
                logger.warn("Header Authorization vazio ou sem Bearer no cadastro-service.");
            }
        }
        return Optional.empty();
    }

    private static Optional<String> getClaimValue(String claimKey, String[] parts) {
        if (parts.length >= 2) {
            // O payload é a segunda parte do JWT
            String payloadString = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode jsonNode = mapper.readTree(payloadString);

            logger.debug("Payload JWT recebido: {}", payloadString); // Isso ajudará a descobrir os nomes exatos

            if (jsonNode.has(claimKey) && !jsonNode.get(claimKey).isNull()) {
                return Optional.of(jsonNode.get(claimKey).asString());
            }
        }
        return Optional.empty();
    }

    public static Optional<UUID> getCurrentUserId() {
        return getClaimFromJwt("sub").map(UUID::fromString);
    }

    public static Optional<Long> getCurrentTenantId() {
        // No SecurityFilter.java do Gateway a claim se chama "tenantId" e foi inserida como String no Auth.
        // Vamos garantir que a conversão ocorra adequadamente.
        return getClaimFromJwt("tenantId").map(Long::valueOf);
    }

    public static Optional<String> getCurrentUserEmail() {
        // Se a claim no JWT não for 'userEmail', e sim 'email' ou 'sub', ajuste aqui:
        return getClaimFromJwt("userEmail");
    }

    public static CurrentUser getCurrentUserInfo() {
        UUID userId = getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USUARIO_NAO_AUTENTICADO));
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

    private static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

}
