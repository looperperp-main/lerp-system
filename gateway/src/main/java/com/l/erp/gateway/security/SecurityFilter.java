package com.l.erp.gateway.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
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

import java.io.IOException;
import java.util.*;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Value("${api.security.jwt.secret}")
    private String secret;

    private final Logger log = LoggerFactory.getLogger(SecurityFilter.class);

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/auth/login", "/auth/tenant/login", "/auth/partner/login", "/auth/refresh", "/auth/logout"
    );

    private static final Map<String, Set<String>> PUBLIC_METHOD_PATHS = Map.of(
            "POST", Set.of("/billing/api/v1/partners")
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        String path = request.getRequestURI();

        // Libera os endpoints do Actuator para métricas do Prometheus
        if (path.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (PUBLIC_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Set<String> publicPathsForMethod = PUBLIC_METHOD_PATHS.get(request.getMethod().toUpperCase());
        if (publicPathsForMethod != null && publicPathsForMethod.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.replace("Bearer ", "");

            try{
                DecodedJWT decodedJWT = JWT.require(Algorithm.HMAC256(secret))
                        .withIssuer("L-ERP-auth-service")
                        .build()
                        .verify(token);
                List<String> permissions = decodedJWT.getClaim("authorities").asList(String.class);
                List<String> roles = decodedJWT.getClaim("roles").asList(String.class);
                Boolean isOwner = decodedJWT.getClaim("isOwner").asBoolean();
                String tenantId = decodedJWT.getClaim("tenantId").asString();

                // Lista final que o Spring vai usar
                String loginType = decodedJWT.getClaim("loginType").asString();

                if ("PARTNER".equals(loginType) && !path.startsWith("/billing/partner/")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }

                List<SimpleGrantedAuthority> grantedAuthorities = new ArrayList<>();

                // Adiciona as roles
                if (roles != null) {
                    for (String role : roles) {
                        grantedAuthorities.add(new SimpleGrantedAuthority(role));
                    }
                }

                // Adiciona as permissões granulares
                if (permissions != null) {
                    for (String perm : permissions) {
                        grantedAuthorities.add(new SimpleGrantedAuthority(perm));
                    }
                }

                var authentication = new UsernamePasswordAuthenticationToken(
                        decodedJWT.getSubject(), null, grantedAuthorities
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);

                String partnerId = decodedJWT.getClaim("partnerId").asString();
                Map<String, String> extraHeaders = new HashMap<>();
                if ("PARTNER".equals(loginType)) {
                    extraHeaders.put("X-Partner-Id", partnerId != null ? partnerId : "");
                } else {
                    extraHeaders.put("X-Tenant-Id", tenantId);
                    extraHeaders.put("X-Is-Owner", String.valueOf(isOwner));
                }

                HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(request){
                    @Override
                    public String getHeader(String name) {
                        if (extraHeaders.containsKey(name)) {
                            return extraHeaders.get(name);
                        }
                        return super.getHeader(name);
                    }

                    @Override
                    public Enumeration<String> getHeaderNames(){
                        var names = Collections.list(super.getHeaderNames());
                        names.addAll(extraHeaders.keySet());
                        return Collections.enumeration(names);
                    }

                    @Override
                    public Enumeration<String> getHeaders(String name){
                        if (extraHeaders.containsKey(name)) {
                            return Collections.enumeration(Collections.singletonList(extraHeaders.get(name)));
                        }
                        return super.getHeaders(name);
                    }
                };

                filterChain.doFilter(wrappedRequest, response);
                return;
            }catch (NullPointerException ex){
                logger.error("JWT token is null or invalid", ex);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }catch (TokenExpiredException exception){
                log.warn("Token expirado: {}", exception.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setHeader("X-Token-Expired", "true");
                return;
            }catch (JWTVerificationException ex){
                log.warn("Token inválido: {}", ex.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }catch (Exception ex){
                log.error("Erro inesperado na validação do token", ex);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }

        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
