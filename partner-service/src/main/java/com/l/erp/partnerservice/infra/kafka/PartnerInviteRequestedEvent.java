package com.l.erp.partnerservice.infra.kafka;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PartnerInviteRequestedEvent(
        UUID partnerReferralId,
        UUID partnerId,
        String partnerName,
        String referralCode,
        Long tenantId,
        String cnpj,
        String razaoSocial,
        String nomeFantasia,
        String emailContato,
        String telefone,
        String planoSugerido,
        OffsetDateTime invitedAt
) {}