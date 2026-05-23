package com.l.erp.cadastroservice.api.dto;

import com.l.erp.common.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record GrupoClienteDTO(
        UUID id,

        @NotBlank(message = "O nome é obrigatório")
        @NoHtml
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        String nome,

        @NoHtml
        @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres")
        String descricao,

        Boolean ativo,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String lastUpdatedBy
) {
}
