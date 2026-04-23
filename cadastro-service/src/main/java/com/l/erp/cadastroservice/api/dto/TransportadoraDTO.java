package com.l.erp.cadastroservice.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record TransportadoraDTO(UUID id,
                                Long tenantId,
                                UUID pessoaId,
                                String pessoaNomeRazao,
                                @Size(max = 20) String rntrc,
                                @Size(max = 20) String modal,
                                @NotNull Boolean ativo,
                                Instant createdAt,
                                Instant updatedAt,
                                UUID createdBy,
                                UUID lastUpdatedBy
) implements Serializable {
}
