package com.l.erp.partnerservice.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ComissaoItemDTO(
        UUID id,
        Long tenantId,
        String razaoSocial,
        String cnpj,
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
