package com.l.erp.billingservice.domain;

/**
 * Valores de {@code billing.commission.commission_model} (coluna String, convenção do repo).
 *
 * <p>Hoje só existe o modelo {@code RECORRENTE} (10% da mensalidade a cada pagamento confirmado).
 * O modelo ANUAL está no roadmap; manter a constante centralizada para que a evolução não espalhe
 * literais pelo código.</p>
 */
public final class CommissionModel {

    public static final String RECORRENTE = "RECORRENTE";

    private CommissionModel() {
    }
}