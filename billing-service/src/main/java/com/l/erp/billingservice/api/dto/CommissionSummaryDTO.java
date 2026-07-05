package com.l.erp.billingservice.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Resumo agregado de comissões de uma competência (item 4 — visão admin).
 * Totais no topo + quebra por parceiro (pendente/pago).
 */
public record CommissionSummaryDTO(
        String competencia,
        BigDecimal totalPendente,
        BigDecimal totalPago,
        int parceirosAPagar,
        List<PorParceiro> porParceiro) {

    public record PorParceiro(UUID partnerId, BigDecimal pendente, BigDecimal pago) {}
}
