package com.l.erp.billingservice.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Item da listagem admin de comissões (tela Pagamentos). */
public record CommissionAdminDTO(
        UUID id,
        UUID partnerId,
        Long tenantId,
        BigDecimal amount,
        String period,
        String status,
        String asaasTransferId,
        OffsetDateTime calculatedAt,
        OffsetDateTime paidAt
) {}
