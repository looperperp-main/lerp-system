package com.l.erp.gateway.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
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

        if (path.startsWith("/auth/login") || path.startsWith("/auth/tenant/login") || path.startsWith("/auth/refresh") || path.startsWith("/auth/logout")) {
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

                //if (path.startsWith("/auth/") && (roles == null || !roles.contains("ROLE_APP_OWNER"))) {
                  //  response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                  //  return;
                //}

                // Lista final que o Spring vai usar
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

                Map<String, String> extraHeaders = new HashMap<>();
                extraHeaders.put("X-Tenant-Id", tenantId);
                extraHeaders.put("X-Is-Owner", String.valueOf(isOwner));

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
            } catch (Exception exception){
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setHeader("X-Token-Expired","true");
                return;
            }

        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
