package com.l.erp.operacoesservice.domain.compras;

import com.l.erp.operacoesservice.domain.compras.enumerators.StatusCotacaoCompraFornecedor;
import com.l.erp.operacoesservice.repository.filter.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Convite/resposta de um fornecedor numa cotação (spec/p2p-compras.md
 * §"cotacao_compra_fornecedor", Fase 5). Único por (cotacao_id, fornecedor_id).
 */
@Getter
@Setter
@Entity
@Table(name = "cotacao_compra_fornecedor", schema = "compras")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CotacaoCompraFornecedor extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cotacao_id", nullable = false)
    private CotacaoCompra cotacao;

    // Ref. cadastro-service (Fornecedor) — sem FK física; precisa estar ativo no convite.
    @NotNull
    @Column(name = "fornecedor_id", nullable = false)
    private UUID fornecedorId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusCotacaoCompraFornecedor status;

    // Ref. cadastro-service (CondicaoPagamento) — obrigatória quando status = RESPONDIDA.
    @Column(name = "condicao_pagamento_id")
    private UUID condicaoPagamentoId;

    @Column(name = "prazo_entrega_dias")
    private Integer prazoEntregaDias;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "valor_frete", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorFrete;

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
