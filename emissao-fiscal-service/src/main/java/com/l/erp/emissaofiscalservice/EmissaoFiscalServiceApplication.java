package com.l.erp.emissaofiscalservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication(scanBasePackages = "com.l.erp")
@EnableDiscoveryClient
@EnableMethodSecurity(securedEnabled = true)
@EnableScheduling
public class EmissaoFiscalServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmissaoFiscalServiceApplication.class, args);
    }
}
