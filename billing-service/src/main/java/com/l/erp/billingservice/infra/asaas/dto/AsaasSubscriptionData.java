package com.l.erp.billingservice.infra.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Dados da assinatura no payload de webhook do Asaas (spec §8.7).
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasSubscriptionData {
    private String id;
    private String status;
    private String customer;
    private String cycle;
    private BigDecimal value;
}