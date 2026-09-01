package com.l.erp.operacoesservice.api.dto;

import java.util.UUID;

public record CurrentUser(UUID id, String email) {
}
