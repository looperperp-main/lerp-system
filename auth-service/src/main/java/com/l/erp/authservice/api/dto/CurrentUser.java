package com.l.erp.authservice.api.dto;

import java.util.UUID;

public record CurrentUser(UUID id, String email) {
}
