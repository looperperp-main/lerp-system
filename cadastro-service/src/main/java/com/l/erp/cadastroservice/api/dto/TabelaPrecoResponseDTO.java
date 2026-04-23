package com.l.erp.cadastroservice.api.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class TabelaPrecoResponseDTO extends RepresentationModel<TabelaPrecoResponseDTO> {
    private UUID id;
    private Long tenantId;
    private String nome;
    private String moeda;
    private Boolean ativa;
    private Boolean padrao;
    private LocalDate inicioVigencia;
    private LocalDate fimVigencia;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID createdBy;
    private UUID lastUpdatedBy;
}
