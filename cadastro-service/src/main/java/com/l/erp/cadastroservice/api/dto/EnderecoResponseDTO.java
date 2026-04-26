package com.l.erp.cadastroservice.api.dto;

import com.l.erp.cadastroservice.domain.enumerators.TipoEndereco;
import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Relation(collectionRelation = "enderecos", itemRelation = "endereco")
public class EnderecoResponseDTO extends RepresentationModel<EnderecoResponseDTO> {
    private UUID id;
    private Long tenantId;
    private TipoEndereco tipo;
    private String logradouro;
    private String numero;
    private String complemento;
    private String bairro;
    private String cidade;
    private String uf;
    private String cep;
    private String ibgeCodigo;
    private String pais;
    private Boolean principal;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID createdBy;
    private UUID lastUpdatedBy;
}
