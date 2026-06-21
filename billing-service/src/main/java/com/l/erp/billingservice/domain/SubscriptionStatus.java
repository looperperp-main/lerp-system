package com.l.erp.billingservice.domain;

/**
 * Valores de {@code billing.subscription.status} (coluna String no repo).
 *
 * <p>Mantém a convenção já existente ({@code ATIVA}, {@code AGUARDANDO_PAGAMENTO}) e adiciona
 * os estados do ciclo de dunning/cancelamento. A migração para enum (spec §14.2) fica para a
 * Fase 4, quando o refactor de status for feito por inteiro.</p>
 */
public final class SubscriptionStatus {

    public static final String AGUARDANDO_PAGAMENTO = "AGUARDANDO_PAGAMENTO";
    public static final String ATIVA = "ATIVA";
    public static final String SUSPENSO = "SUSPENSO";
    public static final String CANCELAMENTO_SOLICITADO = "CANCELAMENTO_SOLICITADO";
    public static final String CANCELADO = "CANCELADO";

    private SubscriptionStatus() {
    }
}