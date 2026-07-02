package com.l.erp.partnerservice.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AssinaturaResumoDTO(
        String status,
        String statusCobranca,
        BigDecimal value,
        String paymentMethod,
        OffsetDateTime activatedAt,
        OffsetDateTime nextDueDate,
        BigDecimal ultimoRepasseValor,
        String ultimoRepassePeriodo
) {}
