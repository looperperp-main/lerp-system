package com.l.erp.cadastroservice.api.dto;

import com.l.erp.cadastroservice.domain.enumerators.FormaPagamento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CondicaoPagamentoParcelaRequestDTO(
        UUID id,
        @NotNull(message = "O ID da Condição de Pagamento é obrigatório")
        UUID condicaoPagamentoId,
        @NotNull(message = "O número da parcela é obrigatório")
        @Positive(message = "O número da parcela deve ser maior que zero")
        Integer numeroParcela,
        @NotNull(message = "Os dias de prazo são obrigatórios")
        @PositiveOrZero(message = "Os dias de prazo não podem ser negativos")
        Integer diasPrazo,
        @NotNull(message = "O percentual é obrigatório")
        @Positive(message = "O percentual deve ser maior que zero")
        BigDecimal percentual,
        @NotNull(message = "A forma de pagamento é obrigatória")
        FormaPagamento formaPagamento,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID lastUpdatedBy
) {
}
