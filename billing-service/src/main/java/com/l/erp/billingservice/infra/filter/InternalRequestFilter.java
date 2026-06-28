package com.l.erp.billingservice.infra.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Garante que apenas requests originados do gateway (com X-User-Id injetado)
 * acessem endpoints protegidos. Substitui a confiança implícita no
 * anyRequest().permitAll() da SecurityConfig.
 */
@Component
public class InternalRequestFilter extends OncePerRequestFilter {

    // Paths que o gateway roteia sem autenticação (ver SecurityConfig do gateway)
    private static final Set<String> PUBLIC_EXACT = Set.of(
            "/api/v1/webhooks/asaas",
            "/api/v1/plans",
            "/actuator/health",
            "/actuator/info"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (isPublic(path, method)) {
            chain.doFilter(request, response);
            return;
        }

        String userId = request.getHeader("X-User-Id");
        if (userId == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\"}");
            return;
        }

        // Popula o SecurityContext com as authorities injetadas pelo gateway (X-Authorities, header
        // protegido contra forja). Habilita @PreAuthorize/@Secured nos endpoints (ex.: REPASSE_EXECUTE).
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, parseAuthorities(request.getHeader("X-Authorities"))));

        chain.doFilter(request, response);
    }

    private List<GrantedAuthority> parseAuthorities(String header) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (header != null && !header.isBlank()) {
            for (String a : header.split(",")) {
                String code = a.trim();
                if (!code.isEmpty()) {
                    authorities.add(new SimpleGrantedAuthority(code));
                }
            }
        }
        return authorities;
    }

    private boolean isPublic(String path, String method) {
        if (PUBLIC_EXACT.contains(path)) return true;
        // Documentação OpenAPI / Swagger UI
        if (path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")) return true;
        // POST /api/v1/partners — registro de parceiro via billing (público no gateway)
        if (HttpMethod.POST.matches(method) && "/api/v1/partners".equals(path)) return true;
        return false;
    }
}