package com.l.erp.partnerservice.util;

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

    private static Optional<String> getClaimFromJwt(String claimKey) {
        HttpServletRequest request = getRequest();
        if (request != null) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String token = authHeader.substring(7);
                    String[] parts = token.split("\\.");
                    if (parts.length >= 2) {
                        String payloadString = new String(Base64.getUrlDecoder().decode(parts[1]));
                        JsonNode jsonNode = mapper.readTree(payloadString);
                        if (jsonNode.has(claimKey) && !jsonNode.get(claimKey).isNull()) {
                            return Optional.of(jsonNode.get(claimKey).asText());
                        }
                    }
                } catch (Exception e) {
                    logger.error("Erro ao ler o JWT no SecurityUtils", e);
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<String> getCurrentUserSub() {
        return getClaimFromJwt("sub");
    }

    public static UUID getCorrelationIdFromRequest(Logger log) {
        HttpServletRequest request = getRequest();
        if (request != null) {
            String headerCorId = request.getHeader("X-Correlation-ID");
            if (headerCorId != null && !headerCorId.isBlank()) {
                try {
                    return UUID.fromString(headerCorId);
                } catch (IllegalArgumentException e) {
                    log.warn("Formato de Correlation ID inválido: {}", headerCorId);
                }
            }
        }
        return UUID.randomUUID();
    }

    private static HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    public static Optional<UUID> getCurrentUserId() {
        return getClaimFromJwt("sub").map(UUID::fromString);
    }
}