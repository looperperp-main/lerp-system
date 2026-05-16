package com.l.erp.authservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtivarContaRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 14, max = 100) String senha,
        @NotBlank String confirmacaoSenha
) {}