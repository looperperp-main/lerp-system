package com.l.erp.cadastroservice.api.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Relation(collectionRelation = "produto_categoria", itemRelation = "produto_categoria")
public class ProdutoCategoriaResponseDTO extends RepresentationModel<ProdutoCategoriaResponseDTO> {
    UUID id;
    Long tenantId;
    String nome;
    String descricao;
    Boolean ativa;
    Instant createdAt;
    Instant updatedAt;
    UUID createdBy;
    UUID lastUpdatedBy;
}
