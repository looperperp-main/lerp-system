package com.l.erp.authservice.infra.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * RestClient resolvendo lb://<service> via Eureka (mesmo padrão de
 * operacoes-service/infra/config/RestClientConfig). O bean @Primary "puro" existe pra sobrar
 * como único candidato não-qualificado no contexto — sem ele, o RestClient.Builder @LoadBalanced
 * era o único bean do tipo e acabava sendo reaproveitado pelo próprio cliente Eureka
 * (EurekaClientAutoConfiguration também busca RestClient.Builder sem qualifier), fazendo o
 * registro no Eureka passar pelo interceptor do LoadBalancer e tentar resolver "localhost"
 * (defaultZone) como nome de serviço — BeanCurrentlyInCreationException em
 * scopedTarget.eurekaClient + "No instances available for localhost".
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
