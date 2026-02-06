package com.l.erp.authservice.api.dto;

public record LoginResponse(String username, String token, String refreshToken) {
}
