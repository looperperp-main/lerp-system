package com.l.erp.cadastroservice.api.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Relation(collectionRelation = "estabelecimentos", itemRelation = "estabelecimento")
public class EstabelecimentoResponseDTO extends RepresentationModel<EstabelecimentoResponseDTO> {
    private UUID id;
    private Long tenantId;
    private String cnpjCompleto;
    private String ordem;
    private Boolean matriz;
    private Boolean proprio;
    private String ie;
    private String im;
    private Boolean ativo;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID createdBy;
    private UUID lastUpdatedBy;
}
