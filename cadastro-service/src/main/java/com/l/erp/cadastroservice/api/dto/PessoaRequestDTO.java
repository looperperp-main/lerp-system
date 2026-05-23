package com.l.erp.cadastroservice.api.dto;

import com.l.erp.cadastroservice.domain.enumerators.TipoPessoa;
import com.l.erp.common.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PessoaRequestDTO(
        @NotNull(message = "O tipo de pessoa (PF/PJ) é obrigatório")
        TipoPessoa tipo,
        @NotBlank
        @NoHtml
        @Size(max = 200)
        String nomeRazao,
        @NoHtml
        @Size(max = 200)
        String apelidoFantasia,
        @NotBlank @Size(max = 18)
        String documento,
        String ie,
        String im,
        String rg,
        LocalDate dataNascimento,
        Boolean ativo
) {
}
