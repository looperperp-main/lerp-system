package com.l.erp.billingservice.infra.asaas;

import com.l.erp.billingservice.infra.asaas.client.AsaasCustomerClient;
import com.l.erp.billingservice.infra.asaas.client.AsaasPaymentClient;
import com.l.erp.billingservice.infra.asaas.client.AsaasSubscriptionClient;
import com.l.erp.billingservice.infra.asaas.dto.AsaasCustomerRequest;
import com.l.erp.billingservice.infra.asaas.dto.AsaasCustomerResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsaasGatewayTest {

    @Mock
    AsaasCustomerClient customerClient;

    @Mock
    AsaasSubscriptionClient subscriptionClient;

    @Mock
    AsaasPaymentClient paymentClient;

    @Mock
    CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    /** CB falso: executa o supplier e, em falha, aplica o fallback — como o resilience4j real. */
    private void stubCircuitBreaker() {
        CircuitBreaker fakeCb = new CircuitBreaker() {
            @Override
            public <T> T run(Supplier<T> toRun, Function<Throwable, T> fallback) {
                try {
                    return toRun.get();
                } catch (Throwable t) {
                    return fallback.apply(t);
                }
            }
        };
        when(circuitBreakerFactory.create(anyString())).thenReturn(fakeCb);
    }

    private AsaasGateway gateway(int maxAttempts) {
        return new AsaasGateway(customerClient, subscriptionClient, paymentClient,
                circuitBreakerFactory, maxAttempts);
    }

    private final AsaasCustomerRequest request = AsaasCustomerRequest.of("Tenant X", "12345678000199", "x@x.com");

    @Test
    void createCustomer_success_returnsId() {
        stubCircuitBreaker();
        when(customerClient.create(request)).thenReturn(new AsaasCustomerResponse("cus_1"));

        assertThat(gateway(3).createCustomer(request)).isEqualTo("cus_1");
        verify(customerClient, times(1)).create(request);
    }

    @Test
    void clientError_4xx_mappedToValidationException_withoutRetry() {
        stubCircuitBreaker();
        when(customerClient.create(request)).thenThrow(badRequest());

        assertThatThrownBy(() -> gateway(3).createCustomer(request))
                .isInstanceOf(AsaasValidationException.class);
        // 4xx é permanente — NUNCA retenta
        verify(customerClient, times(1)).create(request);
    }

    @Test
    void serverError_5xx_isRetriedThenThrowsAsaasException() {
        stubCircuitBreaker();
        when(customerClient.create(request)).thenThrow(serverError());

        assertThatThrownBy(() -> gateway(2).createCustomer(request))
                .isInstanceOf(AsaasException.class)
                .isNotInstanceOf(AsaasValidationException.class);
        // maxAttempts=2 → tenta 2 vezes antes de desistir
        verify(customerClient, times(2)).create(request);
    }

    private static RestClientResponseException badRequest() {
        return new RestClientResponseException(
                "400", HttpStatus.BAD_REQUEST, "Bad Request", null, null, null);
    }

    private static RestClientResponseException serverError() {
        return new RestClientResponseException(
                "500", HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", null, null, null);
    }
}