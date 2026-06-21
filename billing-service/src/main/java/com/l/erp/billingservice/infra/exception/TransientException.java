package com.l.erp.billingservice.infra.exception;

/**
 * Erro TRANSITÓRIO no processamento de webhook (DB indisponível, timeout, deadlock) — spec §28.1.
 *
 * <p>Quando lançada, o {@code WebhookProcessor} libera a chave de idempotência no Redis para
 * que a retentativa do Asaas reprocesse o evento. Erros permanentes (payload inválido, regra
 * de negócio) NÃO usam esta exceção — mantêm a chave e alertam o admin.</p>
 */
public class TransientException extends RuntimeException {
    public TransientException(String message, Throwable cause) {
        super(message, cause);
    }
}