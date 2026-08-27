package com.l.erp.cadastroservice.infra.filter;

import com.l.erp.common.util.Constants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Garante que apenas requests originados do gateway (com X-User-Id injetado e o segredo
 * interno válido, issue #62) acessem endpoints protegidos. Substitui a confiança implícita
 * no anyRequest().permitAll() da SecurityConfig.
 */
@Component
public class InternalRequestFilter extends OncePerRequestFilter {

    @Value("${internal.gateway.secret}")
    private String internalSecret;

    private static final Set<String> PUBLIC_EXACT = Set.of(
            "/actuator/health",
            "/actuator/info"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        if (!hasValidInternalSecret(request)) {
            unauthorized(response);
            return;
        }

        if (request.getHeader(Constants.HEADER_USER_ID) == null) {
            unauthorized(response);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean hasValidInternalSecret(HttpServletRequest request) {
        String received = request.getHeader(Constants.HEADER_INTERNAL_SECRET);
        return received != null && MessageDigest.isEqual(
                received.getBytes(StandardCharsets.UTF_8), internalSecret.getBytes(StandardCharsets.UTF_8));
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\"}");
    }

    private boolean isPublic(String path) {
        if (PUBLIC_EXACT.contains(path)) return true;
        // Actuator loggers: painel de diagnóstico (proxy interno) lê/troca nível de log
        if (path.startsWith("/actuator/loggers")) return true;
        // Documentação OpenAPI / Swagger UI
        return path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui");
    }
}