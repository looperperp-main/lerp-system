package com.l.erp.authservice.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarContaGratisRequest(
        @NotBlank String cnpj,
        @NotBlank @Size(max = 200) String razaoSocial,
        @Size(max = 200) String nomeFantasia,
        @NotBlank @Email @Size(max = 200) String email,
        @NotBlank String senha,
        @Size(max = 20) String telefone
) {}