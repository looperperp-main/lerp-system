package com.l.erp.authservice.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TenantLoginRequest(
        @NotBlank(message = "CNPJ é obrigatório")
        // Alfanumérico-ready (NT 2026.004): 12 caracteres de base (dígitos e/ou letras A-Z) +
        // 2 dígitos verificadores numéricos. O AuthService normaliza (uppercase) antes do lookup.
        @Pattern(regexp = "[A-Za-z0-9]{12}\\d{2}", message = "CNPJ deve ter 12 caracteres de base + 2 dígitos verificadores")
        String cnpj,

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        //@Size(min = 14, message = "Senha deve ter no mínimo 14 caracteres")
        String password
) {
}
