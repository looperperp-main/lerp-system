package com.l.erp.authservice.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record RolePermissionRequestDTO(
        @NotNull(message = "O ID da Role é obrigatório")
        UUID roleId,

        @NotNull(message = "É necessário enviar pelo menos uma permissão")
        List<UUID> permissionIds
) {
}
