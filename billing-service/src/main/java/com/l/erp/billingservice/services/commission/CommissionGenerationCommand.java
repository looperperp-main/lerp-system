package com.l.erp.billingservice.services.commission;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dados que chegam pelo evento {@code partner.commission.calculated} para gerar uma comissão.
 *
 * <p>O {@code amount} já vem calculado pelo partner-service (que detém o referral e a {@code commission_rate}).
 * O billing apenas persiste e deriva a {@code base_value} a partir da própria subscription — ele não
 * recalcula a taxa.</p>
 */
public record CommissionGenerationCommand(
        UUID partnerId,
        Long tenantId,
        String asaasSubscriptionId,
        String asaasPaymentId,
        BigDecimal amount
) {
}