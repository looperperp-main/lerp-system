package com.l.erp.billingservice.infra.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * Payload de webhook do Asaas (spec payments-service.md §8.7 / §23.2).
 *
 * <p>O campo {@code id} é o identificador único POR ENTREGA de webhook — chave de
 * idempotência preferida (§28.2). {@code payment}/{@code subscription}/{@code transfer}
 * vêm preenchidos conforme o tipo do evento.</p>
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasWebhookPayload {

    /** event id do Asaas — único por entrega; chave de idempotência preferida. */
    private String id;

    /** Tipo do evento: PAYMENT_RECEIVED, PAYMENT_OVERDUE, SUBSCRIPTION_INACTIVATED, TRANSFER_COMPLETED, etc. */
    private String event;

    private AsaasPaymentData payment;

    private AsaasSubscriptionData subscription;

    /** Presente em TRANSFER_COMPLETED / TRANSFER_FAILED (§23.2). */
    private AsaasTransferData transfer;
}