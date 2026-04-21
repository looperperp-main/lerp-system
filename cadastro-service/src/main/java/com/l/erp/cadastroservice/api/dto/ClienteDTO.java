package com.l.erp.cadastroservice.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for {@link com.l.erp.cadastroservice.domain.Cliente}
 */
public record ClienteDTO(UUID id, Long tenantId, UUID pessoaId, @Size(max = 50) String codigoInterno,
                         UUID condicaoPagamentoId, UUID grupoClienteId, UUID vendedorId,
                         @PositiveOrZero BigDecimal limiteCredito, @Size(max = 10) String classificacaoRisco,
                         @PositiveOrZero Integer prazoMedioPagamentoDias, @NotNull Boolean ativo,
                         Instant createdAt, Instant updatedAt, UUID createdBy,
                         UUID lastUpdatedBy) implements Serializable {
}