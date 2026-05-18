package com.l.erp.partnerservice.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ClienteDetalheResponseDTO(
        ConviteResponseDTO referral,
        int loginCount,
        OffsetDateTime lastLoginAt,
        int daysActive,
        List<FeatureStatDTO> features,
        List<String> adoptionGaps
) {}