package com.l.erp.billingservice.services.commission;

import com.l.erp.billingservice.domain.Commission;
import com.l.erp.billingservice.domain.CommissionModel;
import com.l.erp.billingservice.domain.CommissionStatus;
import com.l.erp.billingservice.domain.Subscription;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecurrentCommissionStrategyTest {

    private final RecurrentCommissionStrategy strategy = new RecurrentCommissionStrategy();

    @Test
    void getModel_isRecorrente() {
        assertThat(strategy.getModel()).isEqualTo(CommissionModel.RECORRENTE);
    }

    @Test
    void build_monthlyCycle_baseValueEqualsSubscriptionValue() {
        Subscription sub = subscription("MONTHLY", new BigDecimal("179.00"));

        Commission c = strategy.build(command(new BigDecimal("17.90")), sub);

        // base_value é a mensalidade da própria subscription; amount vem PRONTO do partner (não recalcula a taxa)
        assertThat(c.getBaseValue()).isEqualByComparingTo("179.00");
        assertThat(c.getAmount()).isEqualByComparingTo("17.90");
        assertThat(c.getCommissionModel()).isEqualTo(CommissionModel.RECORRENTE);
        assertThat(c.getStatus()).isEqualTo(CommissionStatus.PENDENTE);
        assertThat(c.getAsaasPaymentId()).isEqualTo("pay_1");
        assertThat(c.getTenantId()).isEqualTo(7L);
        assertThat(c.getSubscription()).isSameAs(sub);
        assertThat(c.getPeriod()).isEqualTo(YearMonth.now().toString());
        assertThat(c.getCalculatedAt()).isNotNull();
    }

    @Test
    void build_yearlyCycle_baseValueIsProrated() {
        Subscription sub = subscription("YEARLY", new BigDecimal("1790.00"));

        Commission c = strategy.build(command(new BigDecimal("14.92")), sub);

        // 1790 / 12 = 149.1666... -> 149.17 (HALF_UP, escala 2)
        assertThat(c.getBaseValue()).isEqualByComparingTo("149.17");
    }

    @Test
    void build_nullValue_baseValueIsZero() {
        Subscription sub = subscription("MONTHLY", null);

        Commission c = strategy.build(command(BigDecimal.ZERO), sub);

        assertThat(c.getBaseValue()).isEqualByComparingTo("0");
    }

    private static Subscription subscription(String cycle, BigDecimal value) {
        Subscription s = new Subscription();
        s.setId(UUID.randomUUID());
        s.setTenantId(7L);
        s.setBillingCycle(cycle);
        s.setValue(value);
        return s;
    }

    private static CommissionGenerationCommand command(BigDecimal amount) {
        return new CommissionGenerationCommand(UUID.randomUUID(), 7L, "sub_1", "pay_1", amount);
    }
}