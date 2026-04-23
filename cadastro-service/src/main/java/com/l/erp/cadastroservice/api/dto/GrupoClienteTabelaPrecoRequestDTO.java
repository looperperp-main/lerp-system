package com.l.erp.cadastroservice.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record GrupoClienteTabelaPrecoRequestDTO(
    @NotNull(message = "A lista de tabelas de preço não pode ser nula")
    List<UUID> tabelaPrecoIds
) {
}
