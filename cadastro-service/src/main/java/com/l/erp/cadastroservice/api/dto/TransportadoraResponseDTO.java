package com.l.erp.cadastroservice.api.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class TransportadoraResponseDTO extends RepresentationModel<TransportadoraResponseDTO> {
    private UUID id;
    private Long tenantId;
    private UUID pessoaId;
    private String pessoaNomeRazao;
    private String rntrc;//Registro Nacional de Transportadores Rodoviários de Cargas
    private String modal;
    private Boolean ativo;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID createdBy;
    private UUID lastUpdatedBy;
}

