package com.l.erp.authservice.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(jsr250Enabled = true)
class WebSecurityConfig {

    @Bean
    UserDetailsService userDetailsService() {
        UserDetails user = User.withUsername("usuario@teste.com")
                .password("{noop}Mgk214458")
                .authorities("TENANT_INSERT", "TENANT_READ", "TENANT_DELETE", "TENANT_UPDATE",
                        "ROLE_APP_OWNER", "ROLE_TENANT_OWNER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }
}