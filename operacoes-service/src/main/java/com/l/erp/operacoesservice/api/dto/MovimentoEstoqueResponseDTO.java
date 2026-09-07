package com.l.erp.operacoesservice.api.dto;

import com.l.erp.operacoesservice.domain.estoque.enumerators.OrigemMovimentoEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.TipoMovimentoEstoque;
import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Linha do extrato de GET /api/v1/estoque/movimentos (spec/estoque.md §5.2). {@code origemId} deixa
 * o frontend linkar de volta pro pedido/recebimento que gerou o movimento. {@code ponytail:} sem
 * endpoint de detalhe — a linha do extrato já é o recurso inteiro (§5.2).
 */
@Getter
@Setter
@Relation(collectionRelation = "movimentos", itemRelation = "movimento")
public class MovimentoEstoqueResponseDTO extends RepresentationModel<MovimentoEstoqueResponseDTO> {
    private UUID id;
    private UUID produtoId;
    private UUID depositoId;
    private TipoMovimentoEstoque tipo;
    private OrigemMovimentoEstoque origemTipo;
    private UUID origemId;
    private BigDecimal quantidade;
    private BigDecimal valorUnitario;
    private String motivo;
    private UUID usuarioId;
    private Instant ocorridoEm;
}
