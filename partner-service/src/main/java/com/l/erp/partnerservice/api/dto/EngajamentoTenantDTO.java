package com.l.erp.partnerservice.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Engajamento de um tenant (Visão 360, admin): mesma métrica do painel do parceiro, por tenantId direto. */
public record EngajamentoTenantDTO(
        int loginCount,
        OffsetDateTime lastLoginAt,
        int daysActive,
        List<FeatureStatDTO> features,
        List<String> adoptionGaps
) {}
