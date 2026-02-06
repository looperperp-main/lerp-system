package com.l.erp.gateway.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Value("${api.security.jwt.secret}")
    private String secret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.replace("Bearer ", "");

            DecodedJWT decodedJWT = JWT.require(Algorithm.HMAC256(secret))
                    .withIssuer("L-ERP-auth-service")
                    .build()
                    .verify(token);

            List<String> roles = decodedJWT.getClaim("roles").asList(String.class);
            Boolean isOwner = decodedJWT.getClaim("isOwner").asBoolean();
            Long tenantId = decodedJWT.getClaim("tenantId").asLong();

            // Exemplo de decisão simples:
            String path = request.getRequestURI();
            if (path.startsWith("/auth/") && (roles == null || !roles.contains("ROLE_OWNER"))) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            // Você pode também repassar infos para os microserviços via header:
            request.setAttribute("X-Tenant-Id", tenantId);
            request.setAttribute("X-Is-Owner", isOwner);
        }

        filterChain.doFilter(request, response);
    }
}
