package com.l.erp.billingservice.infra.asaas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request de criação de assinatura recorrente no Asaas (spec §7.4).
 *
 * <p>{@code billingType = UNDEFINED} deixa o pagador escolher boleto, PIX ou cartão.
 * {@code externalReference} carrega o {@code tenantId} para rastreabilidade nos webhooks.</p>
 */
public record AsaasSubscriptionRequest(
        String customer,
        String billingType,
        String cycle,
        BigDecimal value,
        LocalDate nextDueDate,
        String description,
        String externalReference
) {
}