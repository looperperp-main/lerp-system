package com.l.erp.authservice.infra;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.l.erp.authservice.dominio.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;

@Service
public class TokenService {

    @Value( "${api.security.jwt.secret}")
    private String secret;
    public String generateToken(UserAccount user, List<String> roles, boolean isOwner){
        try{
            Algorithm algorithm = Algorithm.HMAC256(secret);

            String token = JWT.create()
                    .withSubject(String.valueOf(user.getId()))
                    .withIssuer("L-ERP-auth-service")
                    .withExpiresAt(generateExpirationDate())
                    .withIssuedAt(Instant.now())
                    .withClaim("tenantId", String.valueOf(user.getTenantId()))
                    .withClaim("roles", roles)
                    .withClaim("isOwner", isOwner)
                    .sign(algorithm);

            return token;
        } catch (JWTCreationException ex){
            throw new RuntimeException("Erro ao gerar token");
        }
    }

    public String validateToken(String token){
        try{
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("L-ERP-auth-service").build().verify(token).getSubject();
        } catch (JWTVerificationException ex){
            return null;
        }
    }

    private Instant generateExpirationDate(){
        return LocalDateTime.now().plus(1, ChronoUnit.HOURS).toInstant(ZoneOffset.of( "-03:00"));
    }
}
