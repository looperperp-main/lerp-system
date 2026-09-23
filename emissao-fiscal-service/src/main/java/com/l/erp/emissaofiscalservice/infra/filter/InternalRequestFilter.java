package com.l.erp.emissaofiscalservice.infra.filter;

import com.l.erp.common.util.Constants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Garante que apenas requests originados do gateway (com X-User-Id injetado e o segredo interno
 * válido) acessem endpoints protegidos — substitui a confiança implícita no anyRequest().permitAll()
 * da SecurityConfig. Porta de operacoes-service/InternalRequestFilter (issue #62).
 *
 * <p>MVP atende só chamada interna via Eureka/headers do gateway (spec §2) — autenticação de
 * chamador externo de verdade fica no backlog da issue #100.</p>
 */
@Component
public class InternalRequestFilter extends OncePerRequestFilter {

    @Value("${internal.gateway.secret}")
    private String internalSecret;

    private static final Set<String> PUBLIC_EXACT = Set.of(
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus"
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

        String userId = request.getHeader(Constants.HEADER_USER_ID);
        if (userId == null) {
            unauthorized(response);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, parseAuthorities(request.getHeader(Constants.HEADER_AUTHORITIES))));

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

    private boolean isPublic(String path) {
        if (PUBLIC_EXACT.contains(path)) return true;
        if (path.startsWith("/actuator/loggers")) return true;
        if (path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")) return true;
        return false;
    }
}
