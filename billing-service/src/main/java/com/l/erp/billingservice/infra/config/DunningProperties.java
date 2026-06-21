package com.l.erp.billingservice.infra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Prazos de dunning — não hardcoded (spec §27.7.2).
 * {@code billing.dunning.grace-period-days} / {@code cancel-after-days} / {@code reminder-before-days}.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "billing.dunning")
public class DunningProperties {
    /** Dias após PAYMENT_OVERDUE até suspender. */
    private int gracePeriodDays = 5;
    /** Dias após PAYMENT_OVERDUE até cancelar. */
    private int cancelAfterDays = 7;
    /** Envia lembrete quando faltam N dias para suspender. */
    private int reminderBeforeDays = 2;
}