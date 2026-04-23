package com.l.erp.cadastroservice.api.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.util.UUID;

@Getter
@Setter
@Relation(collectionRelation = "associacoes", itemRelation = "associacao")
public class GrupoClienteTabelaPrecoResponseDTO extends RepresentationModel<GrupoClienteTabelaPrecoResponseDTO> {
    private UUID tabelaPrecoId;
    private UUID grupoClienteId;
    private Long tenantId;
    private String tabelaPrecoNome; // Útil para mostrar o nome da tabela no frontend
}