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
@Relation(collectionRelation = "clientes", itemRelation = "cliente")
public class ClienteResponseDTO extends RepresentationModel<ClienteResponseDTO> {
    private UUID id;
    private UUID pessoaId;
    private Long tenantId;
    private UUID condicaoPagamentoId;
    private UUID grupoClienteId;
    private UUID vendedorId;
    private String codigoInterno;
    private String nome;
    private BigDecimal limiteCredito;
    private String classificacaoRisco;
    private Integer prazoMedioPagamentoDias;
    private Boolean ativo;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID createdBy;
    private UUID lastUpdatedBy;
}
