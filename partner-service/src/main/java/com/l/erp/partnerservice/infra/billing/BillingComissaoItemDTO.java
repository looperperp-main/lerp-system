package com.l.erp.partnerservice.infra.billing;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BillingComissaoItemDTO(
        UUID id,
        Long tenantId,
        BigDecimal amount,
        String period,
        String status,
        OffsetDateTime calculatedAt,
        OffsetDateTime paidAt
) {}