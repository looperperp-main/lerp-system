package com.l.erp.billingservice.services.commission;

import com.l.erp.billingservice.domain.Commission;
import com.l.erp.billingservice.domain.CommissionModel;
import com.l.erp.billingservice.domain.CommissionStatus;
import com.l.erp.billingservice.domain.Subscription;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.YearMonth;

/**
 * Modelo RECORRENTE — único ativo. O {@code amount} chega pronto do partner-service; aqui o billing só
 * persiste e registra a {@code base_value} (mensalidade da subscription, prorateada se o ciclo for anual).
 * O billing <b>não</b> reaplica a {@code commission_rate} — essa regra é do partner-service.
 */
@Component
public class RecurrentCommissionStrategy implements CommissionStrategy {

    @Override
    public String getModel() {
        return CommissionModel.RECORRENTE;
    }

    @Override
    public Commission build(CommissionGenerationCommand command, Subscription subscription) {
        Commission commission = new Commission();
        commission.setPartnerId(command.partnerId());
        commission.setTenantId(command.tenantId());
        commission.setSubscription(subscription);
        commission.setAmount(command.amount());
        commission.setBaseValue(monthlyBase(subscription));
        commission.setCommissionModel(CommissionModel.RECORRENTE);
        commission.setPeriod(YearMonth.now().toString()); // YYYY-MM
        commission.setStatus(CommissionStatus.PENDENTE);
        commission.setAsaasPaymentId(command.asaasPaymentId()); // UNIQUE — barreira de idempotência
        commission.setCalculatedAt(OffsetDateTime.now());
        return commission;
    }

    /** Mensalidade de referência: o {@code value}, ou {@code value ÷ 12} quando o ciclo é anual. */
    private BigDecimal monthlyBase(Subscription subscription) {
        BigDecimal value = subscription.getValue() != null ? subscription.getValue() : BigDecimal.ZERO;
        if ("YEARLY".equalsIgnoreCase(subscription.getBillingCycle())) {
            return value.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        }
        return value;
    }
}