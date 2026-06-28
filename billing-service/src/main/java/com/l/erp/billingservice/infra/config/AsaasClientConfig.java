package com.l.erp.billingservice.infra.config;

import com.l.erp.billingservice.infra.asaas.AsaasConfig;
import com.l.erp.billingservice.infra.asaas.client.AsaasCustomerClient;
import com.l.erp.billingservice.infra.asaas.client.AsaasPaymentClient;
import com.l.erp.billingservice.infra.asaas.client.AsaasSubscriptionClient;
import com.l.erp.billingservice.infra.asaas.client.AsaasTransferClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;

/**
 * Camada de integração com o Asaas (spec §3.3, §4.4, §13.1/§13.2).
 *
 * <p>Usa HTTP Interface clients (`@HttpExchange`) sobre um {@link RestClient} configurado com o
 * header {@code access_token} — substituto idiomático do OpenFeign no Spring Boot 4. A resiliência
 * (circuit breaker + time limiter) é configurada aqui no {@code asaas-api} e aplicada nas chamadas
 * pelo {@code AsaasGateway} via {@code CircuitBreakerFactory}.</p>
 */
@Configuration
public class AsaasClientConfig {

    @Bean
    public RestClient asaasRestClient(AsaasConfig config) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        // Base URL DEVE terminar com "/" e os paths dos @HttpExchange NÃO começam com "/",
        // senão o Spring trata o path como absoluto e descarta o prefixo /api/v3 (→ 404).
        String baseUrl = config.getBaseUrl().endsWith("/") ? config.getBaseUrl() : config.getBaseUrl() + "/";
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("access_token", config.getApiKey())
                .build();
    }

    @Bean
    public AsaasCustomerClient asaasCustomerClient(RestClient asaasRestClient) {
        return proxyFactory(asaasRestClient).createClient(AsaasCustomerClient.class);
    }

    @Bean
    public AsaasSubscriptionClient asaasSubscriptionClient(RestClient asaasRestClient) {
        return proxyFactory(asaasRestClient).createClient(AsaasSubscriptionClient.class);
    }

    @Bean
    public AsaasPaymentClient asaasPaymentClient(RestClient asaasRestClient) {
        return proxyFactory(asaasRestClient).createClient(AsaasPaymentClient.class);
    }

    @Bean
    public AsaasTransferClient asaasTransferClient(RestClient asaasRestClient) {
        return proxyFactory(asaasRestClient).createClient(AsaasTransferClient.class);
    }

    private HttpServiceProxyFactory proxyFactory(RestClient restClient) {
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
    }

    /**
     * Configura o circuit breaker {@code asaas-api} (spec §13.2): abre com 50% de falha em 10
     * chamadas, espera 30s. O time limiter sobe para 20s — o default da fábrica spring-cloud é 1s,
     * que cortaria chamadas legítimas ao Asaas.
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> asaasCircuitBreakerCustomizer() {
        return factory -> factory.configure(builder -> builder
                        .timeLimiterConfig(TimeLimiterConfig.custom()
                                .timeoutDuration(Duration.ofSeconds(20))
                                .build())
                        .circuitBreakerConfig(CircuitBreakerConfig.custom()
                                .failureRateThreshold(50)
                                .slowCallDurationThreshold(Duration.ofSeconds(8))
                                .waitDurationInOpenState(Duration.ofSeconds(30))
                                .permittedNumberOfCallsInHalfOpenState(3)
                                .slidingWindowSize(10)
                                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                                .build()),
                "asaas-api");
    }
}