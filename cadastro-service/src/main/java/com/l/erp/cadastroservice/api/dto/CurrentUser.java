package com.l.erp.cadastroservice.api.dto;

import java.util.UUID;

public record CurrentUser(UUID id, String email) {
}
