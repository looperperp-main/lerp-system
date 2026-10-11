package com.l.erp.migracaoservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.l.erp")
@ConfigurationPropertiesScan
@EnableDiscoveryClient
@EnableAsync
@EnableScheduling
public class MigracaoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MigracaoServiceApplication.class, args);
    }
}
