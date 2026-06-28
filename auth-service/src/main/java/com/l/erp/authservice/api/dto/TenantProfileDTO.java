package com.l.erp.authservice.api.dto;

import java.time.Instant;

/**
 * Perfil do tenant logado (GET /auth/tenant/me) — usado pelo portal do tenant para pré-preencher
 * o checkout e exibir o banner de trial. Resolvido a partir do header X-Tenant-Id injetado pelo gateway.
 */
public record TenantProfileDTO(
        Long id,
        String name,
        String cnpj,
        String email,
        String status,
        Instant trialStartedAt,
        Instant trialExpiresAt
) {}
