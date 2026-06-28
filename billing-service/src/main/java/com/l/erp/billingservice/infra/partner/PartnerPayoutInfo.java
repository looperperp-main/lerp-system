package com.l.erp.billingservice.infra.partner;

/** Dados de repasse do parceiro lidos de {@code partner.partner} (read-only cross-schema). */
public record PartnerPayoutInfo(String pixKey, String pixKeyType) {
}
