package com.l.erp.operacoesservice.api.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.util.List;
import java.util.UUID;

/** Resposta de GET /api/v1/producao/fichas-tecnicas (spec/modulos/estoque/estoque.md §12, D11, Fase 2). */
@Getter
@Setter
@Relation(collectionRelation = "fichasTecnicas", itemRelation = "fichaTecnica")
public class FichaTecnicaResponseDTO extends RepresentationModel<FichaTecnicaResponseDTO> {
    private UUID id;
    private UUID produtoAcabadoId;
    private Boolean ativo;
    private List<FichaTecnicaItemDTO> itens;
}
