package com.l.erp.partnerservice.infra.kafka;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PartnerApprovedEvent(
        UUID partnerId,
        String name,
        String email,
        String cnpj,
        String crc,
        String phone,
        String referralCode,
        BigDecimal commissionRate,
        String approvedBy,
        OffsetDateTime approvedAt
) {}