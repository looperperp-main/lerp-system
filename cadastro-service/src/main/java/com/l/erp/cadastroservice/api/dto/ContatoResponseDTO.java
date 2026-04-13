package com.l.erp.cadastroservice.api.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Relation(collectionRelation = "contatos", itemRelation = "contato")
public class ContatoResponseDTO extends RepresentationModel<ContatoResponseDTO> {
    private UUID id;
    private Long tenantId;
    private String nome;
    private String tipo;
    private String cargo;
    private String email;
    private String telefone;
    private Boolean ativo;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID createdBy;
    private UUID lastUpdatedBy;
}
