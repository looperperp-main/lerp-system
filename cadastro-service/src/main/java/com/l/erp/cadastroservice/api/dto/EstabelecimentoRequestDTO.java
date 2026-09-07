package com.l.erp.cadastroservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request de filial (matriz é criada/gerida automaticamente por PessoaService — não via este DTO).
 */
public record EstabelecimentoRequestDTO(
        @NotBlank @Size(max = 18) String cnpjCompleto,
        String ie,
        String im,
        Boolean ativo
) {
}
