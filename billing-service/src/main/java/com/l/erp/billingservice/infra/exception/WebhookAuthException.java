package com.l.erp.billingservice.infra.exception;

/**
 * Lançada quando o token do webhook do Asaas é inválido ou ausente (spec §5.1).
 * Mapeada para HTTP 401 — único caso em que o webhook responde não-2xx.
 */
public class WebhookAuthException extends RuntimeException {
    public WebhookAuthException(String message) {
        super(message);
    }
}