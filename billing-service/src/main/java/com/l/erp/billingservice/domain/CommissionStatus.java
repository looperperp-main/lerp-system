package com.l.erp.billingservice.domain;

/**
 * Valores de {@code billing.commission.status} (coluna String, convenção do repo).
 *
 * <p>{@code EM_TRANSFERENCIA} é usado pelo payout (Fase 6) entre o disparo da transferência PIX e a
 * confirmação via webhook {@code TRANSFER_COMPLETED}/{@code TRANSFER_FAILED}.</p>
 */
public final class CommissionStatus {

    public static final String PENDENTE = "PENDENTE";
    public static final String EM_TRANSFERENCIA = "EM_TRANSFERENCIA";
    public static final String PAGO = "PAGO";
    public static final String CANCELADO = "CANCELADO";

    private CommissionStatus() {
    }
}