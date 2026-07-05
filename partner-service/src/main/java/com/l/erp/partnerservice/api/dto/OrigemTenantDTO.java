package com.l.erp.partnerservice.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Origem de um tenant na Visão 360 do admin: o contador que o indicou e o
 * estado da indicação. Retornado por {@code GET /api/v1/partners/referrals/by-tenant/{tenantId}}.
 */
public record OrigemTenantDTO(
        String contador,
        String crc,
        String referralCode,
        BigDecimal commissionRate,
        String status,
        OffsetDateTime invitedAt,
        OffsetDateTime activatedAt,
        OffsetDateTime convertedAt
) {}
