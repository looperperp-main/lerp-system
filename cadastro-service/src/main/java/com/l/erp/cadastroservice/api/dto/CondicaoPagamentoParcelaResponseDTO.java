package com.l.erp.cadastroservice.api.dto;

import com.l.erp.cadastroservice.domain.enumerators.FormaPagamento;
import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Relation(collectionRelation = "parcelas", itemRelation = "parcela")
public class CondicaoPagamentoParcelaResponseDTO extends RepresentationModel<CondicaoPagamentoParcelaResponseDTO> {
    private UUID id;
    private UUID condicaoPagamentoId;
    private Integer numeroParcela;
    private Integer diasPrazo;
    private BigDecimal percentual;
    private FormaPagamento formaPagamento;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID createdBy;
    private UUID lastUpdatedBy;
}
