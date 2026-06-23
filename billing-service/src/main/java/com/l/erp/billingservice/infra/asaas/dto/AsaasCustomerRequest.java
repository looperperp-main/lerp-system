package com.l.erp.billingservice.infra.asaas.dto;

/**
 * Request de criação de customer no Asaas (spec §7.3).
 * Campos de endereço/telefone são opcionais — o billing só dispõe de razão social, CNPJ e e-mail
 * no momento do checkout (a Tenant completa vive no auth-service).
 */
public record AsaasCustomerRequest(
        String name,
        String cpfCnpj,
        String email
) {
    public static AsaasCustomerRequest of(String name, String cpfCnpj, String email) {
        return new AsaasCustomerRequest(name, cpfCnpj, email);
    }
}