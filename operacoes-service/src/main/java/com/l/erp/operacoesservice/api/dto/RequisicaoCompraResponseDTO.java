package com.l.erp.operacoesservice.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusRequisicaoCompra;
import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Resposta da requisição de compra (spec/p2p-compras.md §"requisicao_compra", Fase 1b).
 * itens/historico só vêm preenchidos no detalhe (GET /{id}); a listagem devolve o resumo,
 * por isso NON_NULL pra omitir no JSON (mesmo padrão de PedidoResponseDTO).
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(collectionRelation = "requisicoes", itemRelation = "requisicao")
public class RequisicaoCompraResponseDTO extends RepresentationModel<RequisicaoCompraResponseDTO> {
    private UUID id;
    private Long tenantId;
    private Long numero;
    private StatusRequisicaoCompra status;
    private UUID solicitanteId;
    private UUID depositoId;
    private String justificativa;
    private LocalDate dataNecessidade;
    private UUID aprovadorId;
    private Instant aprovadoEm;
    private String motivoReprovacao;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID createdBy;
    private UUID lastUpdatedBy;
    private List<RequisicaoCompraItemResponseDTO> itens;
    private List<CompraStatusHistoricoDTO> historico;
}
