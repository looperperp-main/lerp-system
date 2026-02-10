package com.l.erp.authservice.infra.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {
    @Value("${api.security.jwt.secret}")
    private String secret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path.startsWith("/auth/login") || path.startsWith("/auth/refresh") || path.startsWith("/auth/logout")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.replace("Bearer ", "");

            DecodedJWT decodedJWT = JWT.require(Algorithm.HMAC256(secret))
                    .withIssuer("L-ERP-auth-service")
                    .build()
                    .verify(token);

            List<String> roles = decodedJWT.getClaim("roles").asList(String.class);

            List<SimpleGrantedAuthority> authorities = roles == null ? List.of()
                    : roles.stream().map(SimpleGrantedAuthority::new).toList();

            var authentication = new UsernamePasswordAuthenticationToken(
                    decodedJWT.getSubject(), null, authorities
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
