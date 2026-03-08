package com.l.erp.authservice.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;


@EnableWebSecurity
@Configuration
@EnableMethodSecurity(jsr250Enabled = true)
class WebSecurityConfig {
    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
                .withUser("usuario@teste.com").password("Mgk214458").roles("ROLE_APP_OWNER") // Sem o prefixo ROLE_
                .authorities("TENANT_INSERT", "TENANT_READ", "TENANT_DELETE", "TENANT_UPDATE");
    }
}
