package com.l.erp.authservice.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RedefinirSenhaRequest(
        @NotBlank String token,
        @NotBlank String novaSenha,
        @NotBlank String confirmacaoSenha
) {}