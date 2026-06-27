package com.l.erp.partnerservice.infra.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Garante que apenas requests originados do gateway (com X-User-Id injetado)
 * acessem endpoints protegidos. Substitui a confiança implícita no
 * anyRequest().permitAll() da SecurityConfig.
 */
@Component
public class InternalRequestFilter extends OncePerRequestFilter {

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

        if (request.getHeader("X-User-Id") == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isPublic(String path) {
        if (PUBLIC_EXACT.contains(path)) return true;
        // Documentação OpenAPI / Swagger UI
        if (path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")) return true;
        // GET /api/v1/partners/cnpj/** — consulta pública de CNPJ
        if (path.startsWith("/api/v1/partners/cnpj/")) return true;
        return false;
    }
}