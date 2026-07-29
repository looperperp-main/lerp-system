package com.l.erp.fiscalservice.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * Botão liga/desliga do split payment (Fatia C).
 *
 * <p>A adesão ao split é faseada — 2026 é período de teste e cada empresa entra num momento
 * diferente, conforme o PSP/arranjo homologa. Por isso o default é <b>desligado</b> e existe
 * allowlist por tenant, além do interruptor global.
 *
 * <p>O flag <b>não altera o cálculo</b> dos tributos: altera o contrato de saída — desligado,
 * o resultado não carrega os campos de split e nenhum informe é emitido à Plataforma Pública.
 *
 * @param enabled liga o split para todos os tenants
 * @param tenants ids de tenant com split ligado, usado quando {@code enabled=false}
 */
@ConfigurationProperties("fiscal.split-payment")
public record SplitPaymentProperties(boolean enabled, Set<String> tenants) {

    public SplitPaymentProperties {
        tenants = tenants == null ? Set.of() : tenants;
    }

    public boolean habilitadoPara(String tenantId) {
        return enabled || (tenantId != null && tenants.contains(tenantId));
    }
}
