package com.l.erp.authservice.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EsqueciSenhaTenantRequest(
        @NotBlank @Email @Size(max = 200) String email,
        @NotBlank String cnpj
) {}