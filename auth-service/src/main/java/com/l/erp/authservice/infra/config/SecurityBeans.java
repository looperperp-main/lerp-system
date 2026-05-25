package com.l.erp.authservice.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class SecurityBeans {

    @Bean
    public PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = new LinkedHashMap<>();
        // Novos hashes usam Argon2id (mais resistente a GPU/ASIC que BCrypt)
        encoders.put("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        encoders.put("bcrypt", new BCryptPasswordEncoder(12));

        DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder("argon2", encoders);
        // Hashes BCrypt legados sem prefixo {encoding} continuam sendo verificados
        delegating.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder(12));
        return delegating;
    }
}