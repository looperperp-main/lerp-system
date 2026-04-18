package com.l.erp.cadastroservice.api.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Relation(collectionRelation = "vendedor", itemRelation = "vendedor")
public class VendedorResponseDTO extends RepresentationModel<VendedorResponseDTO> {
    UUID id;
    Long tenantId;
    UUID pessoaId;
    String nome;
    BigDecimal comissaoPercentual;
    Boolean ativo;
    Instant createdAt;
    Instant updatedAt;
    UUID createdBy;
    UUID lastUpdatedBy;
}
