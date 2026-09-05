package com.l.erp.operacoesservice.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.l.erp.operacoesservice.domain.vendas.enumerators.ModalidadeFrete;
import com.l.erp.operacoesservice.domain.vendas.enumerators.StatusPedido;
import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Resposta do pedido (spec/o2c-vendas.md §5/§10, Fase 4). itens/historico só vêm preenchidos no
 * detalhe (GET /{id}); a listagem (GET) devolve o resumo, por isso NON_NULL pra omitir no JSON.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(collectionRelation = "pedidos", itemRelation = "pedido")
public class PedidoResponseDTO extends RepresentationModel<PedidoResponseDTO> {
    private UUID id;
    private Long tenantId;
    private Long numero;
    private StatusPedido status;
    private UUID clienteId;
    private UUID vendedorId;
    private UUID condicaoPagamentoId;
    private UUID transportadoraId;
    private UUID depositoId;
    private ModalidadeFrete modalidadeFrete;
    private BigDecimal valorFrete;
    private BigDecimal valorItens;
    private BigDecimal valorDesconto;
    private BigDecimal valorTotal;
    private BigDecimal valorTotalNf;
    private BigDecimal valorIbs;
    private BigDecimal valorCbs;
    private BigDecimal valorIs;
    private BigDecimal valorIss;
    private BigDecimal valorRetencoes;
    private LocalDate dataEmissao;
    private LocalDate dataValidade;
    private Instant dataConfirmacao;
    private Instant dataExpedicao;
    private Instant dataFaturamento;
    private Instant dataCancelamento;
    private String motivoCancelamento;
    private String observacao;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID createdBy;
    private UUID lastUpdatedBy;
    private List<PedidoItemResponseDTO> itens;
    private List<PedidoStatusHistoricoDTO> historico;
    private List<ParcelaFaturamentoDTO> parcelas;
}
