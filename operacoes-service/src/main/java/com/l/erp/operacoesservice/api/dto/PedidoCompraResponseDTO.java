package com.l.erp.operacoesservice.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusPedidoCompra;
import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Mesmo padrão de RequisicaoCompraResponseDTO (Fase 1b). */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(collectionRelation = "pedidos", itemRelation = "pedido")
public class PedidoCompraResponseDTO extends RepresentationModel<PedidoCompraResponseDTO> {

    private UUID id;
    private Long numero;
    private UUID fornecedorId;
    private UUID condicaoPagamentoId;
    private UUID depositoId;
    private UUID requisicaoId;
    private UUID cotacaoFornecedorId;
    private StatusPedidoCompra status;
    private LocalDate dataEmissao;
    private LocalDate dataPrevisaoEntrega;
    private BigDecimal valorFrete;
    private BigDecimal valorTotal;
    private UUID aprovadorId;
    private Instant aprovadoEm;
    private String motivoCancelamento;
    private String observacao;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID createdBy;
    private UUID lastUpdatedBy;
    private List<PedidoCompraItemResponseDTO> itens;
    private List<CompraStatusHistoricoDTO> historico;
}
