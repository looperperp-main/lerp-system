package com.l.erp.authservice.infra;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.infra.config.Roles;
import com.l.erp.authservice.util.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class TokenService {

    @Value( "${api.security.jwt.secret}")
    private String secret;
    public String generateToken(UserAccount user, List<String> roles, boolean isOwner, List<String> permissions){
        try{
            return createJWT(user,
                    roles, isOwner,
                    permissions, String.valueOf(user.getEmail()),
                    user.getDisplayName(), user.getTenant(), Constants.ADMIN);

        } catch (JWTCreationException ex){
            throw new RuntimeException("Erro ao gerar token", ex);
        }
    }

    /**
     * Gera token JWT para login de usuário de tenant (sistema principal)
     * Inclui informações adicionais do tenant para contexto da aplicação
     */
    public String generateTenantUserToken(UserAccount user, List<String> permissions, Tenant tenant) {
        try {
            return createJWT(user, List.of(Roles.ROLE_TENANT_USER), false, permissions,user.getEmail(), user.getDisplayName(), tenant, Constants.TENANT);
        } catch (JWTCreationException ex) {
            throw new RuntimeException("Erro ao gerar token para usuário do tenant", ex);
        }
    }

    /**
     * Gera token JWT para parceiro (portal de parceiros)
     * Não contém tenantId — contém partnerId e loginType=PARTNER
     */
    public String generatePartnerToken(UserAccount user) {
        try {
            return JWT.create()
                    .withSubject(String.valueOf(user.getId()))
                    .withIssuer("L-ERP-auth-service")
                    .withExpiresAt(generateExpirationDate())
                    .withIssuedAt(Instant.now())
                    .withClaim("roles", List.of("ROLE_PARTNER"))
                    .withClaim("isOwner", false)
                    .withClaim("authorities", List.of())
                    .withClaim("userEmail", user.getEmail())
                    .withClaim("displayName", user.getDisplayName())
                    .withClaim("partnerId", user.getPartnerId() != null ? user.getPartnerId().toString() : null)
                    .withClaim("loginType", Constants.PARTNER)
                    .sign(Algorithm.HMAC256(secret));
        } catch (JWTCreationException ex) {
            throw new RuntimeException("Erro ao gerar token para parceiro", ex);
        }
    }

    /**
     * create a JWT based on the user's info
     * @param user user
     * @param roles roles
     * @param isOwner if is an Owner
     * @param permissions list of permissions
     * @param email email
     * @param displayName display name
     * @param tenant tenant info
     * @param loginType login type (TENANT)
     * @return Jwt String
     */
    private String createJWT(UserAccount user, List<String> roles, boolean isOwner, List<String> permissions, String email, String displayName, Tenant tenant, String loginType){
        return JWT.create()
                .withSubject(String.valueOf(user.getId()))
                .withIssuer("L-ERP-auth-service")
                .withExpiresAt(generateExpirationDate())
                .withIssuedAt(Instant.now())
                .withClaim("roles", roles)
                .withClaim("isOwner", isOwner)
                .withClaim("authorities", permissions)
                .withClaim("userEmail", email)
                .withClaim("displayName", displayName)
                .withClaim("tenantId", tenant.getId())
                .withClaim("tenantName", tenant.getName())
                .withClaim("tenantCnpj", tenant.getCnpj())
                .withClaim("loginType", loginType)
                .sign(Algorithm.HMAC256(secret));
    }

    public String generateInvitationToken(Long tenantId, String cnpj, String referralId) {
        try {
            return JWT.create()
                    .withSubject(String.valueOf(tenantId))
                    .withIssuer("L-ERP-auth-service")
                    .withExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                    .withIssuedAt(Instant.now())
                    .withClaim("type", "INVITATION")
                    .withClaim("cnpj", cnpj)
                    .withClaim("referralId", referralId)
                    .sign(Algorithm.HMAC256(secret));
        } catch (JWTCreationException ex) {
            throw new RuntimeException("Erro ao gerar token de ativação", ex);
        }
    }

    private Instant generateExpirationDate(){
        return Instant.now().plus(1, ChronoUnit.HOURS);
    }
}
