package com.l.erp.cadastroservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EnderecoRequestDTO(
        @NotBlank @Size(max = 20) String tipo,
        @NotBlank @Size(max = 200) String logradouro,
        @Size(max = 20) String numero,
        @Size(max = 100) String complemento,
        @Size(max = 100) String bairro,
        @NotBlank @Size(max = 100) String cidade,
        @NotBlank @Size(max = 2) String uf,
        @NotBlank @Size(max = 9) String cep,
        @Size(max = 10) String ibgeCodigo,
        @Size(max = 60) String pais,
        @NotNull Boolean principal
) {
}
