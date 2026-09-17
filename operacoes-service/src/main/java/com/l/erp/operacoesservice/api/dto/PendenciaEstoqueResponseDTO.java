package com.l.erp.operacoesservice.api.dto;

import com.l.erp.operacoesservice.domain.estoque.enumerators.PendenciaTipoEstoque;
import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.Instant;
import java.util.UUID;

/** Resposta de GET /api/v1/estoque/pendencias (spec/modulos/estoque/estoque.md §12, RN-EST-12). */
@Getter
@Setter
@Relation(collectionRelation = "pendencias", itemRelation = "pendencia")
public class PendenciaEstoqueResponseDTO extends RepresentationModel<PendenciaEstoqueResponseDTO> {
    private UUID id;
    private UUID produtoId;
    private UUID depositoId;
    private PendenciaTipoEstoque tipo;
    private UUID movimentoId;
    private Boolean resolvida;
    private Instant criadaEm;
    private Instant resolvidaEm;
    private UUID resolvidoPor;
}
