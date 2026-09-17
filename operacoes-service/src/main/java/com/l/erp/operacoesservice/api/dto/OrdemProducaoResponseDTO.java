package com.l.erp.operacoesservice.api.dto;

import com.l.erp.operacoesservice.domain.estoque.enumerators.StatusOrdemProducao;
import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Resposta de GET /api/v1/producao/ordens (spec/modulos/estoque/estoque.md §12, D11, Fase 2). */
@Getter
@Setter
@Relation(collectionRelation = "ordens", itemRelation = "ordem")
public class OrdemProducaoResponseDTO extends RepresentationModel<OrdemProducaoResponseDTO> {
    private UUID id;
    private UUID produtoAcabadoId;
    private UUID depositoId;
    private BigDecimal quantidadePlanejada;
    private StatusOrdemProducao status;
    private Instant concluidaEm;
    private Instant criadaEm;
}
