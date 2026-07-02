package com.l.erp.partnerservice.infra.billing;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BillingAssinaturaResumoDTO(
        String status,
        String statusCobranca,
        BigDecimal value,
        String paymentMethod,
        OffsetDateTime activatedAt,
        OffsetDateTime nextDueDate,
        BigDecimal ultimoRepasseValor,
        String ultimoRepassePeriodo
) {}
