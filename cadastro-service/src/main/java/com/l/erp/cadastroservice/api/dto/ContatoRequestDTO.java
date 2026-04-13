package com.l.erp.cadastroservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ContatoRequestDTO (
        @NotBlank @Size(max = 200) String nome,
        @NotBlank @Size(max = 20) String tipo,
        @Size(max = 100) String cargo,
        @Size(max = 200) String email,
        @Size(max = 20) String telefone,
        @NotNull Boolean ativo
) {
}
