package com.l.erp.operacoesservice.api.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Resposta de GET /api/v1/estoque/saldos (spec/estoque.md §5.1). {@code estoqueMinimo}/
 * {@code abaixoMinimo} ficam null até a Fase E6 (badge, cadastro-service) — os campos já existem
 * no contrato pra o frontend não precisar mudar depois.
 */
@Getter
@Setter
@Relation(collectionRelation = "saldos", itemRelation = "saldo")
public class EstoqueSaldoResponseDTO extends RepresentationModel<EstoqueSaldoResponseDTO> {
    private UUID produtoId;
    private UUID depositoId;
    private BigDecimal quantidade;
    private BigDecimal estoqueMinimo;
    private Boolean abaixoMinimo;
    private Instant atualizadoEm;
}
