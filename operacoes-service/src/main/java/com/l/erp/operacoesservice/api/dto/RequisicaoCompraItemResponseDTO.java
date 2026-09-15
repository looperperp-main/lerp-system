package com.l.erp.operacoesservice.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Item da requisição na resposta (aninhado em RequisicaoCompraResponseDTO.itens). */
public record RequisicaoCompraItemResponseDTO(
        UUID id,
        UUID produtoId,
        BigDecimal quantidade,
        String observacao
) {
}
