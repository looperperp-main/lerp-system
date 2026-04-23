package com.l.erp.cadastroservice.api.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class FornecedorResponseDTO extends RepresentationModel<FornecedorResponseDTO> {
    private UUID id;
    private Long tenantId;
    private UUID pessoaId;
    private Boolean ativo;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID createdBy;
    private UUID lastUpdatedBy;
}