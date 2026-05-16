package com.l.erp.partnerservice.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ConviteResponseDTO(
        UUID referralId,
        String cnpj,
        String razaoSocial,
        String emailContato,
        String status,
        Integer followupAttempts,
        OffsetDateTime invitedAt,
        OffsetDateTime tokenExpiresAt
) {}