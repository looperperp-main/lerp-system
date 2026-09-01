package com.l.erp.operacoesservice.infra.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * RestClient @LoadBalanced resolvendo lb://cadastro-service via Eureka (spec/o2c-vendas.md §2) —
 * padrão novo no monorepo: até aqui só existiam clients para APIs externas (Asaas, ViaCEP, CNPJá).
 * Sem OpenFeign: usa só o que o stack Spring Cloud já traz (spring-cloud-starter-loadbalancer).
 */
@Configuration
public class RestClientConfig {

    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
