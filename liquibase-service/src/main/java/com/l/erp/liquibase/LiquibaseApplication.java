package com.l.erp.liquibase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MigrationApplication {

    public static void main(String[] args) {
        // Esta aplicação sobe, roda o Liquibase e desliga automaticamente
        SpringApplication.run(MigrationApplication.class, args);
    }
}