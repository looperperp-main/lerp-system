package com.l.erp.cadastroservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.l.erp")
@EnableDiscoveryClient
public class CadastroServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CadastroServiceApplication.class, args);
    }

}
