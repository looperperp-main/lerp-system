package com.l.erp.partnerservice.infra.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class BillingClient {

    private static final Logger log = LoggerFactory.getLogger(BillingClient.class);

    private final RestClient restClient;

    public BillingClient(@Value("${billing.service.url:http://localhost:8088}") String billingUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(billingUrl)
                .build();
    }

    public BillingExtratoDTO getExtrato(UUID partnerId) {
        log.info("Consultando extrato de comissões para partnerId={}", partnerId);
        try {
            return restClient.get()
                    .uri("/api/v1/commissions/extrato?partnerId={id}", partnerId)
                    .retrieve()
                    .body(BillingExtratoDTO.class);
        } catch (Exception e) {
            log.error("Falha ao consultar extrato no billing-service para partnerId={}", partnerId, e);
            return null;
        }
    }
}