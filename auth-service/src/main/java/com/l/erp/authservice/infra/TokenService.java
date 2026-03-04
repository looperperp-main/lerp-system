package com.l.erp.authservice.infra;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.l.erp.authservice.dominio.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class TokenService {

    @Value( "${api.security.jwt.secret}")
    private String secret;
    public String generateToken(UserAccount user, List<String> roles, boolean isOwner, List<String> permissions){
        try{
            Algorithm algorithm = Algorithm.HMAC256(secret);

            return JWT.create()
                    .withSubject(String.valueOf(user.getId()))
                    .withIssuer("L-ERP-auth-service")
                    .withExpiresAt(generateExpirationDate())
                    .withIssuedAt(Instant.now())
                    .withClaim("tenantId", String.valueOf(user.getTenant().getId()))
                    .withClaim("userEmail", String.valueOf(user.getEmail()))
                    .withClaim("authorities", permissions)
                    .withClaim("roles", roles)
                    .withClaim("isOwner", isOwner)
                    .sign(algorithm);
        } catch (JWTCreationException ex){
            throw new RuntimeException("Erro ao gerar token");
        }
    }

    private Instant generateExpirationDate(){
        return LocalDateTime.now().plusHours(1).toInstant(ZoneOffset.of( "-03:00"));
    }
}
