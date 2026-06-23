package com.l.erp.billingservice.infra.asaas;

import com.l.erp.billingservice.infra.asaas.client.AsaasCustomerClient;
import com.l.erp.billingservice.infra.asaas.client.AsaasPaymentClient;
import com.l.erp.billingservice.infra.asaas.client.AsaasSubscriptionClient;
import com.l.erp.billingservice.infra.asaas.dto.AsaasCustomerRequest;
import com.l.erp.billingservice.infra.asaas.dto.AsaasCustomerResponse;
import com.l.erp.billingservice.infra.asaas.dto.AsaasListResponse;
import com.l.erp.billingservice.infra.asaas.dto.AsaasPaymentResponse;
import com.l.erp.billingservice.infra.asaas.dto.AsaasPixQrCodeResponse;
import com.l.erp.billingservice.infra.asaas.dto.AsaasSubscriptionRequest;
import com.l.erp.billingservice.infra.asaas.dto.AsaasSubscriptionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Supplier;

/**
 * Fachada de saída para o Asaas (spec §3.3, §4.1, §4.4). Centraliza a resiliência sobre os
 * sub-clients HTTP Interface: cada chamada passa pelo circuit breaker {@code asaas-api} e por um
 * retry com backoff exponencial (1s → 2s → 4s).
 *
 * <p>Mapeamento de erros: 4xx (exceto 408/429) → {@link AsaasValidationException} (permanente,
 * NUNCA retentado); 5xx, timeout e falha de conexão → {@link AsaasException} (transitório,
 * retentado). Operações de money-out (transfers, §28.4) NÃO devem usar retry — ficam na Fase 6.</p>
 */
@Component
public class AsaasGateway {

    private static final Logger log = LoggerFactory.getLogger(AsaasGateway.class);
    private static final String CB_NAME = "asaas-api";

    private final AsaasCustomerClient customerClient;
    private final AsaasSubscriptionClient subscriptionClient;
    private final AsaasPaymentClient paymentClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final int maxAttempts;

    public AsaasGateway(AsaasCustomerClient customerClient,
                        AsaasSubscriptionClient subscriptionClient,
                        AsaasPaymentClient paymentClient,
                        CircuitBreakerFactory<?, ?> circuitBreakerFactory,
                        @Value("${asaas.max-retry-attempts:3}") int maxAttempts) {
        this.customerClient = customerClient;
        this.subscriptionClient = subscriptionClient;
        this.paymentClient = paymentClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.maxAttempts = maxAttempts;
    }

    /** Cria o customer e devolve o id Asaas (cus_xxx). */
    public String createCustomer(AsaasCustomerRequest req) {
        AsaasCustomerResponse resp = call("createCustomer", () -> customerClient.create(req));
        if (resp == null || resp.id() == null) {
            throw new AsaasException("Resposta inválida do Asaas ao criar customer");
        }
        return resp.id();
    }

    public AsaasSubscriptionResponse createSubscription(AsaasSubscriptionRequest req) {
        AsaasSubscriptionResponse resp = call("createSubscription", () -> subscriptionClient.create(req));
        if (resp == null || resp.id() == null) {
            throw new AsaasException("Resposta inválida do Asaas ao criar subscription");
        }
        return resp;
    }

    public AsaasSubscriptionResponse getSubscription(String asaasSubscriptionId) {
        return call("getSubscription", () -> subscriptionClient.get(asaasSubscriptionId));
    }

    /** Primeira cobrança da assinatura — boleto/PIX inicial para devolver ao frontend (§7.2). */
    public AsaasPaymentResponse getFirstPayment(String asaasSubscriptionId) {
        AsaasListResponse<AsaasPaymentResponse> list =
                call("listPayments", () -> paymentClient.listBySubscription(asaasSubscriptionId));
        if (list == null || list.data() == null || list.data().isEmpty()) {
            throw new AsaasException("Nenhuma cobrança gerada para a assinatura " + asaasSubscriptionId);
        }
        return list.data().getFirst();
    }

    public AsaasPixQrCodeResponse getPixQrCode(String asaasPaymentId) {
        return call("getPixQrCode", () -> paymentClient.getPixQrCode(asaasPaymentId));
    }

    private <T> T call(String op, Supplier<T> action) {
        AsaasException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return circuitBreakerFactory.create(CB_NAME).run(action, t -> {
                    throw mapError(op, t);
                });
            } catch (AsaasValidationException e) {
                throw e; // permanente — não retenta
            } catch (AsaasException e) {
                last = e;
                if (attempt < maxAttempts) {
                    log.warn("Asaas {} falhou (tentativa {}/{}) — {}", op, attempt, maxAttempts, e.getMessage());
                    backoff(attempt);
                }
            }
        }
        throw last != null ? last : new AsaasException("Falha ao chamar Asaas em " + op);
    }

    private AsaasException mapError(String op, Throwable t) {
        if (t instanceof AsaasException ae) {
            return ae;
        }
        if (t instanceof RestClientResponseException re) {
            int status = re.getStatusCode().value();
            String body = re.getResponseBodyAsString();
            if (status >= 400 && status < 500 && status != 408 && status != 429) {
                return new AsaasValidationException(
                        "Asaas rejeitou " + op + " (HTTP " + status + "): " + body);
            }
            return new AsaasException("Asaas falhou em " + op + " (HTTP " + status + "): " + body);
        }
        return new AsaasException("Falha de comunicação com Asaas em " + op + ": " + t.getMessage());
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(1000L * (1L << (attempt - 1))); // 1s, 2s, 4s...
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new AsaasException("Interrompido durante backoff de retry ao Asaas");
        }
    }
}