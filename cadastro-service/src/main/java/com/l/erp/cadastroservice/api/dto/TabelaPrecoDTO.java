package com.l.erp.cadastroservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TabelaPrecoDTO(
    UUID id,
    Long tenantId,
    @NotBlank(message = "O nome da tabela de preço é obrigatório")
    @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
    String nome,
    @NotBlank(message = "A moeda é obrigatória")
    @Size(max = 3, message = "A moeda deve ter no máximo 3 caracteres")
    String moeda,
    Boolean ativa,
    Boolean padrao,
    @NotNull(message = "O início de vigência é obrigatório")
    LocalDate inicioVigencia,
    LocalDate fimVigencia,
    Instant createdAt,
    Instant updatedAt,
    UUID createdBy,
    UUID lastUpdatedBy
) implements Serializable {
}
