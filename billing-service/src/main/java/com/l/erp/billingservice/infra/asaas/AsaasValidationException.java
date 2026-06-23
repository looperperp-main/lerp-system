package com.l.erp.billingservice.infra.asaas;

/**
 * Erro PERMANENTE do Asaas (4xx — CNPJ inválido, cliente já existe, payload rejeitado).
 *
 * <p>Diferente de {@link AsaasException} (falha transitória, retentável), esta NUNCA é retentada
 * pelo {@code AsaasGateway} — corresponde ao {@code ignore-exceptions} da config Resilience4j
 * (spec §4.4, §13.2).</p>
 */
public class AsaasValidationException extends AsaasException {
    public AsaasValidationException(String message) {
        super(message);
    }
}