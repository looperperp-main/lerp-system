package com.l.erp.billingservice.services.webhook;

import com.l.erp.billingservice.infra.asaas.dto.AsaasWebhookPayload;

/**
 * Contrato dos handlers de evento de webhook do Asaas (spec §14.2).
 * Cada implementação declara o tipo de evento que trata e a ação correspondente.
 */
public interface WebhookEventHandler {

    /** Tipo do evento Asaas tratado (ex.: {@code PAYMENT_RECEIVED}). */
    String getEventType();

    /** Processa o evento. Exceções transitórias devem ser {@code TransientException}. */
    void handle(AsaasWebhookPayload payload);
}