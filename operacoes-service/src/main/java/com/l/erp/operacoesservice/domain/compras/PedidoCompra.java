package com.l.erp.operacoesservice.domain.compras;

import com.l.erp.operacoesservice.domain.compras.enumerators.StatusPedidoCompra;
import com.l.erp.operacoesservice.repository.filter.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.hibernate.annotations.ColumnDefault;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Pedido de compra ao fornecedor (spec/p2p-compras.md §"pedido_compra", Fase 2). */
@Getter
@Setter
@Entity
@Table(name = "pedido_compra", schema = "compras")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PedidoCompra extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "numero", nullable = false)
    private Long numero;

    // Ref. cadastro-service (Fornecedor) — sem FK física, valida ativo=true via API (RN-P2P-02).
    @NotNull
    @Column(name = "fornecedor_id", nullable = false)
    private UUID fornecedorId;

    // Ref. cadastro-service (CondicaoPagamento) — obrigatória (RN-P2P-03), sem FK física.
    @NotNull
    @Column(name = "condicao_pagamento_id", nullable = false)
    private UUID condicaoPagamentoId;

    // Ref. cadastro-service (Deposito) — sem FK física.
    @NotNull
    @Column(name = "deposito_id", nullable = false)
    private UUID depositoId;

    // Origem: preenchido quando o pedido nasce de uma requisição de compra aprovada.
    @Column(name = "requisicao_id")
    private UUID requisicaoId;

    // Origem: preenchido quando o pedido nasce do encerramento de uma cotação.
    @Column(name = "cotacao_fornecedor_id")
    private UUID cotacaoFornecedorId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    private StatusPedidoCompra status;

    @Column(name = "data_emissao")
    private LocalDate dataEmissao;

    @Column(name = "data_previsao_entrega")
    private LocalDate dataPrevisaoEntrega;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "valor_frete", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorFrete;

    @NotNull
    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "aprovador_id")
    private UUID aprovadorId;

    @Column(name = "aprovado_em")
    private Instant aprovadoEm;

    @Size(max = 500)
    @Column(name = "motivo_cancelamento", length = 500)
    private String motivoCancelamento;

    @Size(max = 500)
    @Column(name = "observacao", length = 500)
    private String observacao;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @NotNull
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "last_updated_by")
    private UUID lastUpdatedBy;
}
