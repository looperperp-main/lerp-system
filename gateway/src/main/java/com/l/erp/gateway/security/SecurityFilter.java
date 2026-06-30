package com.l.erp.gateway.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.Claim;
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
            "/auth/login", "/auth/tenant/login", "/auth/partner/login", "/auth/refresh", "/auth/logout",
            "/auth/ativar", "/auth/criar-conta", "/auth/tenant/esqueci-senha", "/auth/redefinir-senha",
            "/partner/api/v1/partners/cnpj", "/billing/api/v1/webhooks/asaas"
    );

    // Method-specific exact-match public paths (avoids startsWith subpath leakage)
    private static final Map<String, Set<String>> PUBLIC_METHOD_PATHS = Map.of(
            "POST", Set.of("/billing/api/v1/partners"),
            "GET", Set.of("/billing/api/v1/plans")
    );

    /**
     * Processes incoming HTTP requests and applies security filtering logic to determine
     * whether a request should be allowed, rejected, or requires further processing.
     * Handles pre-flight (OPTIONS) requests, public endpoint filtering, and JWT token validation.
     *
     * @param request     The HTTP servlet request containing details about the client's request.
     * @param response    The HTTP servlet response to send back status or error details as necessary.
     * @param filterChain The filter chain to pass the request and response to the next filter if allowed.
     * @throws ServletException If any servlet-specific error occurs during request processing.
     * @throws IOException      If an I/O error occurs during request processing.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        String path = request.getRequestURI();

        if ("/actuator/health".equals(path) || "/actuator/info".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        Set<String> publicPathsForMethod = PUBLIC_METHOD_PATHS.get(request.getMethod().toUpperCase());
        if (publicPathsForMethod != null && publicPathsForMethod.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (validateJWTToken(request, response, filterChain, authHeader, path)) return;
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    /**
     * Validates the given JWT token from the request's Authorization header and processes the associated claims.
     * If valid, sets the authentication context and appends additional headers to the request before passing it
     * along the filter chain. Handles token expiration, missing tokens, and invalid tokens with appropriate HTTP responses.
     *
     * @param request      The HTTP servlet request containing the client request details.
     * @param response     The HTTP servlet response to modify or send back error statuses if needed.
     * @param filterChain  The filter chain to continue processing the request after validation.
     * @param authHeader   The Authorization header from the request, expected to contain the JWT token.
     * @param path         The request path for determining specific token handling rules.
     * @return             {@code true} if the token is successfully validated or an error response has been set, 
     *                     {@code false} if the validation was skipped (e.g., missing authHeader).
     */
    private boolean validateJWTToken(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain, String authHeader, String path) {
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
                String tenantId = extractTenantId(decodedJWT);

                // Lista final que o Spring vai usar
                String loginType = decodedJWT.getClaim("loginType").asString();

                if (verifyPartnerURL(response, path, loginType)) return true;

                List<SimpleGrantedAuthority> grantedAuthorities = new ArrayList<>();

                addRolesAndPermissions(roles, grantedAuthorities, permissions);

                var authentication = new UsernamePasswordAuthenticationToken(
                        decodedJWT.getSubject(), null, grantedAuthorities
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);

                Map<String, String> extraHeaders = getExtraHeaders(decodedJWT, loginType, tenantId, isOwner, grantedAuthorities);

                HttpServletRequest wrappedRequest = getWrappedRequest(request, extraHeaders);

                filterChain.doFilter(wrappedRequest, response);
                return true;
            }catch (NullPointerException ex){
                logger.error("JWT token is null or invalid", ex);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return true;
            }catch (TokenExpiredException exception){
                log.warn("Token expirado: {}", exception.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setHeader("X-Token-Expired", "true");
                return true;
            }catch (JWTVerificationException ex){
                log.warn("Token inválido: {}", ex.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return true;
            }catch (Exception ex){
                log.error("Erro inesperado na validação do token", ex);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return true;
            }

        }
        return false;
    }

    /**
     * Creates a wrapped {@link HttpServletRequest} that overrides the behavior of header-related
     * methods to include additional headers specified in the provided map.
     *
     * @param request      The original {@link HttpServletRequest} to be wrapped.
     * @param extraHeaders A map containing additional header names and their corresponding values
     *                     to be added to the request.
     * @return A wrapped {@link HttpServletRequest} instance that includes the extra headers along
     *         with the headers from the original request.
     */
    /**
     * Headers de identidade derivados do JWT. O cliente NUNCA pode fornecê-los: o wrapper
     * mascara qualquer valor de entrada com esses nomes (comparação case-insensitive, pois
     * nomes de header HTTP são case-insensitive) e só expõe o valor injetado pelo gateway.
     * Defense-in-depth: mesmo que algum caminho de proxy leia o header fora do wrapper, o
     * valor forjado pelo cliente já foi descartado aqui.
     */
    private static final Set<String> PROTECTED_HEADERS = Set.of(
            "x-user-id", "x-user-email", "x-tenant-id", "x-is-owner", "x-partner-id", "x-authorities"
    );

    private static boolean isProtected(String name) {
        return name != null && PROTECTED_HEADERS.contains(name.toLowerCase());
    }

    private static @NonNull HttpServletRequest getWrappedRequest(HttpServletRequest request, Map<String, String> extraHeaders) {
        return new HttpServletRequestWrapper(request){
            @Override
            public String getHeader(String name) {
                if (extraHeaders.containsKey(name)) {
                    return extraHeaders.get(name);
                }
                // Strip de header interno forjado pelo cliente (não injetado pelo gateway nesta request).
                if (isProtected(name)) {
                    return null;
                }
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaderNames(){
                var names = new ArrayList<String>();
                for (String n : Collections.list(super.getHeaderNames())) {
                    if (!isProtected(n)) {
                        names.add(n);
                    }
                }
                names.addAll(extraHeaders.keySet());
                return Collections.enumeration(names);
            }

            @Override
            public Enumeration<String> getHeaders(String name){
                if (extraHeaders.containsKey(name)) {
                    return Collections.enumeration(Collections.singletonList(extraHeaders.get(name)));
                }
                if (isProtected(name)) {
                    return Collections.emptyEnumeration();
                }
                return super.getHeaders(name);
            }
        };
    }

    /**
     * Verifies if the given request path and login type adhere to the partner-specific URL rules.
     * Responds with a "403 Forbidden" status if the login type is "PARTNER" and the path does not begin
     * with "/billing/partner/".
     *
     * @param response  The HTTP servlet response used to set the status code if the URL is invalid.
     * @param path      The request path to validate against partner-specific URL rules.
     * @param loginType The type of login (e.g., "PARTNER") used to determine the validation logic.
     * @return {@code true} if the request is invalid and a "403 Forbidden" response has been set.
     *         {@code false} if the request is valid or does not require validation.
     */
    private static final Set<String> PARTNER_ALLOWED_PREFIXES = Set.of(
            "/billing/partner/",
            "/partner/api/v1/partners/me",
            "/partner/api/v1/partners/cnpj/",
            "/api/v1/partners/me"
    );

    private static boolean verifyPartnerURL(@NonNull HttpServletResponse response, String path, String loginType) {
        if ("PARTNER".equals(loginType)) {
            boolean allowed = PARTNER_ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
            if (!allowed) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return true;
            }
        }
        return false;
    }

    /**
     * Constructs and returns a map of additional headers based on the provided JWT claims, login type, tenant ID, 
     * and ownership status.
     *
     * @param decodedJWT the decoded JWT from which claims are extracted to generate extra headers.
     * @param loginType the type of login (e.g., "PARTNER") determining the header construction logic.
     * @param tenantId the tenant identifier to include in the headers if applicable.
     * @param isOwner a flag indicating if the user is the owner, used to determine header values.
     * @return a map containing key-value pairs for the extra headers to be added to the request.
     */
    private static @NonNull Map<String, String> getExtraHeaders(DecodedJWT decodedJWT, String loginType, String tenantId, Boolean isOwner,
                                                                List<SimpleGrantedAuthority> grantedAuthorities) {
        String partnerId = decodedJWT.getClaim("partnerId").asString();
        String userEmail = decodedJWT.getClaim("userEmail").asString();
        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put("X-User-Id", decodedJWT.getSubject());
        extraHeaders.put("X-User-Email", userEmail != null ? userEmail : "");
        if ("PARTNER".equals(loginType)) {
            extraHeaders.put("X-Partner-Id", partnerId != null ? partnerId : "");
        } else {
            extraHeaders.put("X-Tenant-Id", tenantId);
            extraHeaders.put("X-Is-Owner", String.valueOf(isOwner));
        }
        // Roles + permissões (authorities) para os serviços downstream autorizarem por permissão
        // (ex.: billing exige REPASSE_EXECUTE). Header protegido contra forja pelo wrapper.
        String authorities = grantedAuthorities.stream()
                .map(SimpleGrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.joining(","));
        extraHeaders.put("X-Authorities", authorities);
        return extraHeaders;
    }

    /**
     * Extracts the {@code tenantId} claim as a String, regardless of whether it was
     * serialized as a JSON number (Long, current DB type) or a JSON string (String/UUID).
     * java-jwt's {@code asString()} returns {@code null} for numeric claims and
     * {@code asLong()} returns {@code null} for textual ones, so we try both.
     *
     * @param decodedJWT the verified JWT.
     * @return the tenant id as a String, or {@code null} if the claim is absent/null.
     */
    private static String extractTenantId(DecodedJWT decodedJWT) {
        Claim claim = decodedJWT.getClaim("tenantId");
        if (claim.isMissing() || claim.isNull()) {
            return null;
        }
        String asString = claim.asString();
        if (asString != null) {
            return asString;
        }
        Long asLong = claim.asLong();
        return asLong != null ? String.valueOf(asLong) : null;
    }

    /**
     * Adds roles and permissions to the provided list of granted authorities.
     *
     * @param roles              A list of role names to be added as granted authorities.
     * @param grantedAuthorities A list of granted authorities to which roles and permissions will be added.
     * @param permissions        A list of permission names to be added as granted authorities.
     */
    private static void addRolesAndPermissions(List<String> roles, List<SimpleGrantedAuthority> grantedAuthorities, List<String> permissions) {
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
    }
}
