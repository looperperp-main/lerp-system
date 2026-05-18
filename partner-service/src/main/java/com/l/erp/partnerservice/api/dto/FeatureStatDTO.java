package com.l.erp.partnerservice.api.dto;

import java.time.OffsetDateTime;

public record FeatureStatDTO(
        String featureKey,
        String label,
        int accessCount,
        OffsetDateTime lastAccessedAt
) {}