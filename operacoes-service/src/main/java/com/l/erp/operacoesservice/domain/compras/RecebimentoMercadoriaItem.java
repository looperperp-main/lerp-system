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
 * Item do recebimento de mercadoria (spec/p2p-compras.md §"recebimento_mercadoria_item", Fase 3).
 * {@code pedidoItemId} referencia o {@link PedidoCompraItem} sendo recebido — o {@code produtoId}
 * (e o tipo MERCADORIA/SERVICO, RN-P2P-11) é lido a partir dele, não duplicado aqui.
 */
@Getter
@Setter
@Entity
@Table(name = "recebimento_mercadoria_item", schema = "compras")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecebimentoMercadoriaItem extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recebimento_id", nullable = false)
    private RecebimentoMercadoria recebimento;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_item_id", nullable = false)
    private PedidoCompraItem pedidoItem;

    @NotNull
    @DecimalMin(value = "0.0001")
    @Column(name = "quantidade", nullable = false, precision = 15, scale = 4)
    private BigDecimal quantidade;

    // Preço efetivo da NF — pode divergir do preco_unitario do pedido.
    @NotNull
    @DecimalMin(value = "0.0")
    @Column(name = "preco_unitario_nf", nullable = false, precision = 15, scale = 4)
    private BigDecimal precoUnitarioNf;

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
