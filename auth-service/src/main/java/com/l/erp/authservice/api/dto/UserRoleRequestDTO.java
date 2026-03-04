package com.l.erp.authservice.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record UserRoleRequestDTO(
    @NotNull(message = "O ID do Usuário é obrigatório")
    UUID userId,

    @NotNull(message = "É necessário enviar pelo menos uma Role")
    List<UUID> roleIds

) {
}
