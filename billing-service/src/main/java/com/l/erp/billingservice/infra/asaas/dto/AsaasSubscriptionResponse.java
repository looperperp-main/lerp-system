package com.l.erp.billingservice.infra.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

/**
 * Response de assinatura do Asaas (criação e consulta). {@code nextDueDate} é a fonte da verdade
 * para o vencimento — nunca calcular com {@code plusDays(30/365)} (spec §8.3, §28.3).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasSubscriptionResponse(
        String id,
        String status,
        LocalDate nextDueDate
) {
}