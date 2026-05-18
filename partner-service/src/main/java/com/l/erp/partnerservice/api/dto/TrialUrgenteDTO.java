package com.l.erp.partnerservice.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TrialUrgenteDTO(
        UUID referralId,
        String razaoSocial,
        String cnpj,
        OffsetDateTime trialExpiresAt,
        long daysLeft
) {}