package com.l.erp.cadastroservice.api.dto;

import com.l.erp.cadastroservice.domain.enumerators.TipoPessoa;
import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;


@Getter
@Setter
@Relation(collectionRelation = "pessoas", itemRelation = "pessoa")
public class PessoaResponseDTO extends RepresentationModel<PessoaResponseDTO> {
    private UUID id;
    private Long tenantId;
    private TipoPessoa tipo;
    private String nomeRazao;
    private String apelidoFantasia;
    private String documento;
    private String ie;
    private String im;
    private String rg;
    private LocalDate dataNascimento;
    private String email;
    private String telefone;
    private Boolean ativo;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID createdBy;
    private UUID lastUpdatedBy;
}
