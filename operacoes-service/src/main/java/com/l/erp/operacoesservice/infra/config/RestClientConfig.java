package com.l.erp.operacoesservice.infra.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * RestClient @LoadBalanced resolvendo lb://cadastro-service via Eureka (spec/o2c-vendas.md §2) —
 * padrão novo no monorepo: até aqui só existiam clients para APIs externas (Asaas, ViaCEP, CNPJá).
 * Sem OpenFeign: usa só o que o stack Spring Cloud já traz (spring-cloud-starter-loadbalancer).
 *
 * O bean @Primary "puro" existe pra sobrar como único candidato não-qualificado no contexto —
 * sem ele, o RestClient.Builder @LoadBalanced era o único bean do tipo e acabava sendo
 * reaproveitado pelo próprio cliente Eureka (EurekaClientAutoConfiguration também busca
 * RestClient.Builder sem qualifier), fazendo o registro no Eureka passar pelo interceptor do
 * LoadBalancer e tentar resolver "localhost" (defaultZone) como nome de serviço — mesmo bug
 * corrigido em auth-service/infra/config/RestClientConfig.
 */
@Configuration
public class RestClientConfig {

    @Bean
    @Primary
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
