package com.l.erp.partnerservice.infra.billing;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BillingComissaoItemDTO(
        UUID id,
        Long tenantId,
        BigDecimal amount,
        BigDecimal baseValue,
        BigDecimal percentual,
        String modelo,
        String plano,
        String period,
        String status,
        OffsetDateTime calculatedAt,
        OffsetDateTime paidAt
) {}
