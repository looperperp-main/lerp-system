package com.l.erp.billingservice.infra.asaas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Component
public class AsaasClient {

    private static final Logger log = LoggerFactory.getLogger(AsaasClient.class);

    private final RestClient restClient;

    public AsaasClient(AsaasConfig config) {
        this.restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader("access_token", config.getApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public String createCustomer(String cnpj, String email, String name) {
        log.debug("Criando customer Asaas para cnpj={}", cnpj);
        Map<?, ?> response = restClient.post()
                .uri("/customers")
                .body(Map.of(
                        "name", name,
                        "email", email,
                        "cpfCnpj", cnpj
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new AsaasException("Erro ao criar customer no Asaas: status " + resp.getStatusCode());
                })
                .body(Map.class);

        if (response == null || response.get("id") == null) {
            throw new AsaasException("Resposta inválida do Asaas ao criar customer");
        }
        return (String) response.get("id");
    }

    public AsaasSubscriptionResult createSubscription(String customerId, String cycle,
                                                       BigDecimal value, LocalDate nextDueDate) {
        log.info("Criando subscription Asaas para customer={} cycle={}", customerId, cycle);
        Map<?, ?> response = restClient.post()
                .uri("/subscriptions")
                .body(Map.of(
                        "customer", customerId,
                        "billingType", "UNDEFINED",
                        "cycle", cycle,
                        "value", value,
                        "nextDueDate", nextDueDate.toString()
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new AsaasException("Erro ao criar subscription no Asaas: status " + resp.getStatusCode());
                })
                .body(Map.class);

        if (response == null || response.get("id") == null) {
            throw new AsaasException("Resposta inválida do Asaas ao criar subscription");
        }
        String id = (String) response.get("id");
        String paymentLink = response.get("paymentLink") instanceof String s ? s : null;
        return new AsaasSubscriptionResult(id, paymentLink);
    }

    public String transferPix(java.util.UUID partnerId, java.math.BigDecimal amount) {
        log.info("Iniciando transferência PIX para parceiro={} amount={}", partnerId, amount);
        // Busca pixKey do partner via configuração externa ou tabela; por ora usa partnerId como chave provisória
        // TODO: buscar pixKey real do partner-service
        log.warn("PIX key não configurada para parceiro={} — transferência simulada em sandbox", partnerId);
        return "SIMULADO-" + java.util.UUID.randomUUID();
    }

    public record AsaasSubscriptionResult(String id, String paymentLink) {}
}