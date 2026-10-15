package com.l.erp.authservice.infra.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.l.erp.common.util.Constants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    @Value("${api.security.jwt.secret}")
    private String secret;

    @Value("${internal.gateway.secret}")
    private String internalSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        String path = request.getRequestURI();
        if (path.startsWith("/auth/login") || path.startsWith("/auth/partner/login") || path.startsWith("/auth/refresh") || path.startsWith("/auth/logout")) {
            filterChain.doFilter(request, response);
            return;
        }

        // /actuator/loggers escreve nível de log em runtime: só aceita chamada interna (segredo do gateway),
        // nunca JWT de usuário. Sem segredo válido não autentica e o SecurityConfig nega.
        if (path.startsWith("/actuator/loggers")) {
            String received = request.getHeader(Constants.HEADER_INTERNAL_SECRET);
            if (received != null && MessageDigest.isEqual(
                    received.getBytes(StandardCharsets.UTF_8), internalSecret.getBytes(StandardCharsets.UTF_8))) {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        Constants.AUTHORITY_INTERNAL, null, List.of(new SimpleGrantedAuthority(Constants.AUTHORITY_INTERNAL))));
            }
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.replace("Bearer ", "");

            try {
                DecodedJWT decodedJWT = JWT.require(Algorithm.HMAC256(secret))
                        .withIssuer("L-ERP-auth-service")
                        .build()
                        .verify(token);

                List<String> roles = decodedJWT.getClaim("roles").asList(String.class);
                List<String> authoritiesList = decodedJWT.getClaim("authorities").asList(String.class);
                String email = decodedJWT.getClaim("userEmail").asString();

                // Unificando roles e authorities
                java.util.List<SimpleGrantedAuthority> grantedAuthorities = new java.util.ArrayList<>();

                // Adiciona as roles
                if (roles != null) {
                    for (String role : roles) {
                        grantedAuthorities.add(new SimpleGrantedAuthority(role));
                    }
                }

                // Adiciona as permissões (authorities)
                if (authoritiesList != null) {
                    for (String perm : authoritiesList) {
                        grantedAuthorities.add(new SimpleGrantedAuthority(perm));
                    }
                }

                var authentication = new UsernamePasswordAuthenticationToken(
                        decodedJWT.getSubject(), null, grantedAuthorities
                );
                Map<String, Object> details = new HashMap<>();
                details.put("email", email);
                authentication.setDetails(details);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (TokenExpiredException ex) {
                log.warn("Token expirado: {}", ex.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setHeader("X-Token-Expired", "true");
                return;
            } catch (JWTVerificationException ex) {
                log.warn("Token inválido: {}", ex.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
