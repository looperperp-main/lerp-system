package com.l.erp.cadastroservice.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resposta de GET /api/v1/interno/estoque-config (spec/estoque.md §5.1/E6) — só o campo que o
 * operacoes-service precisa pro badge "abaixo do mínimo", um por produto no depósito consultado.
 */
public record EstoqueConfigRefDTO(UUID produtoId, BigDecimal estoqueMinimo) {
}
