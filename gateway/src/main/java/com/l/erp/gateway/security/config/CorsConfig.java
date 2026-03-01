package com.l.erp.gateway.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

//    @Value("${cors.allowed-origins}")
//    private String allowedOrigins;
//
//    @Value("${cors.allowed-methods}")
//    private String allowedMethods;
//
//    @Value("${cors.allowed-headers}")
//    private String allowedHeaders;
//
//    @Value("${cors.allow-credentials}")
//    private boolean allowCredentials;

    @Value("${cors.allowed-origins:http://localhost:4200,http://localhost:4201}")
    private String allowedOrigins;

    @Value("${cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}")
    private String allowedMethods;

    @Value("${cors.allowed-headers:Authorization,Content-Type,X-Requested-With,Accept,Origin,Access-Control-Request-Method,Access-Control-Request-Headers}")
    private String allowedHeaders;

    @Value("${cors.allow-credentials:true}")
    private boolean allowCredentials;
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(split(allowedOrigins));
        config.setAllowedMethods(split(allowedMethods));
        config.setAllowedHeaders(split(allowedHeaders));
        config.setAllowCredentials(allowCredentials);

        config.addExposedHeader("Authorization");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    private List<String> split(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .toList();
    }
}
