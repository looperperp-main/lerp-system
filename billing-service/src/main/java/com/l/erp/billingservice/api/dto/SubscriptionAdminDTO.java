package com.l.erp.billingservice.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Item da listagem admin de assinaturas (tela Assinaturas). */
public record SubscriptionAdminDTO(
        UUID id,
        Long tenantId,
        String planType,
        String billingCycle,
        BigDecimal value,
        String status,
        String asaasSubscriptionId,
        OffsetDateTime nextDueDate,
        OffsetDateTime createdAt
) {}
