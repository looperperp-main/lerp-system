package com.l.erp.operacoesservice.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusCotacaoCompra;
import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Mesmo padrão de PedidoCompraResponseDTO (Fase 2). */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(collectionRelation = "cotacoes", itemRelation = "cotacao")
public class CotacaoCompraResponseDTO extends RepresentationModel<CotacaoCompraResponseDTO> {

    private UUID id;
    private Long numero;
    private UUID requisicaoId;
    private StatusCotacaoCompra status;
    private LocalDate dataLimiteResposta;
    private UUID depositoId;
    private UUID cotacaoFornecedorVencedorId;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID createdBy;
    private UUID lastUpdatedBy;
    private List<CotacaoCompraItemResponseDTO> itens;
    private List<CotacaoCompraFornecedorResponseDTO> fornecedores;
    private List<CompraStatusHistoricoDTO> historico;
}
