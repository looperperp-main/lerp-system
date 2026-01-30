package com.l.erp.liquibase;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@SpringBootApplication
public class LiquibaseApplication {

    public static void main(String[] args) {
        // Esta aplicação sobe, roda o Liquibase e desliga automaticamente
        ConfigurableApplicationContext context = SpringApplication.run(LiquibaseApplication.class, args);

        // Teste simples para ver se a propriedade foi carregada
        String url = context.getEnvironment().getProperty("spring.datasource.url");
        System.out.println("DEBUG: URL do Banco carregada: " + url);

        String liquibaseEnabled = context.getEnvironment().getProperty("spring.liquibase.enabled");
        System.out.println("DEBUG: Liquibase Enabled? " + liquibaseEnabled);

        String liquibaseDropfirst = context.getEnvironment().getProperty("spring.liquibase.drop-first");
        System.out.println("DEBUG: Drop First? " + liquibaseDropfirst);
    }

    @Bean
    public SpringLiquibase liquibase(DataSource dataSource) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");

        // Configurações do seu application.properties que precisamos passar manualmente aqui
        liquibase.setDropFirst(false);
        liquibase.setShouldRun(true);
        return liquibase;
    }
}