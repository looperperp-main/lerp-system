package com.l.erp.operacoesservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication(scanBasePackages = "com.l.erp")
@EnableDiscoveryClient
@EnableMethodSecurity(securedEnabled = true)
public class OperacoesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OperacoesServiceApplication.class, args);
    }
}
