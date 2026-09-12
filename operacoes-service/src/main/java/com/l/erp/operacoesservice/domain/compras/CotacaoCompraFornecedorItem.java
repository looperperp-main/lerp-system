package com.l.erp.operacoesservice.domain.compras;

import com.l.erp.operacoesservice.repository.filter.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Preço unitário ofertado por um fornecedor pra um item da cotação (spec/p2p-compras.md
 * §"cotacao_compra_fornecedor_item", Fase 5). Único por (cotacao_fornecedor_id, cotacao_item_id).
 */
@Getter
@Setter
@Entity
@Table(name = "cotacao_compra_fornecedor_item", schema = "compras")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CotacaoCompraFornecedorItem extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cotacao_fornecedor_id", nullable = false)
    private CotacaoCompraFornecedor cotacaoFornecedor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cotacao_item_id", nullable = false)
    private CotacaoCompraItem cotacaoItem;

    @NotNull
    @DecimalMin(value = "0.0")
    @Column(name = "preco_unitario", nullable = false, precision = 15, scale = 4)
    private BigDecimal precoUnitario;

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
