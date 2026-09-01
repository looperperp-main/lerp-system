package com.l.erp.fiscalservice;

import com.l.erp.fiscalservice.infra.config.SplitPaymentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.l.erp")
@EnableDiscoveryClient
@EnableConfigurationProperties(SplitPaymentProperties.class)
public class FiscalServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FiscalServiceApplication.class, args);
    }
}