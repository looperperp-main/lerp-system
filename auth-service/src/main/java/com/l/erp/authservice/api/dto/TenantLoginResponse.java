package com.l.erp.authservice.api.dto;

public record TenantLoginResponse(
        String username,
        String token,
        String refreshToken,
        Long tenantId,
        String tenantName,
        String tenantCnpj
) {
}
