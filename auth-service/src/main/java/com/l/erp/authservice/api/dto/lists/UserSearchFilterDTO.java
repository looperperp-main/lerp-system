package com.l.erp.authservice.api.dto.lists;

public record UserSearchFilterDTO(
    Long tenantId,
    String displayName,
    Boolean active) {
}
