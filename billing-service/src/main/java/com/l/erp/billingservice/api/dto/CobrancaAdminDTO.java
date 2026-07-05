package com.l.erp.billingservice.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Cobrança de uma assinatura (drill-down do admin) — espelho enxuto do payment do Asaas. */
public record CobrancaAdminDTO(
        String id,
        String status,
        BigDecimal value,
        LocalDate dueDate,
        String invoiceUrl) {
}
