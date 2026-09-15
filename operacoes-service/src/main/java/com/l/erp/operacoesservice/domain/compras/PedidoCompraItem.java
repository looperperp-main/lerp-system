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
import org.hibernate.annotations.ColumnDefault;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Item do pedido de compra (spec/p2p-compras.md §"pedido_compra_item", Fase 2). */
@Getter
@Setter
@Entity
@Table(name = "pedido_compra_item", schema = "compras")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PedidoCompraItem extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private PedidoCompra pedido;

    // Ref. cadastro-service (Produto) — sem FK física, valida via API.
    @NotNull
    @Column(name = "produto_id", nullable = false)
    private UUID produtoId;

    @NotNull
    @DecimalMin(value = "0.0001")
    @Column(name = "quantidade", nullable = false, precision = 15, scale = 4)
    private BigDecimal quantidade;

    @NotNull
    @DecimalMin(value = "0.0")
    @Column(name = "preco_unitario", nullable = false, precision = 15, scale = 4)
    private BigDecimal precoUnitario;

    // Acumulada pelos recebimentos CONFIRMADOS (Fase 3 — recebimento, fora do escopo desta fatia).
    @NotNull
    @ColumnDefault("0")
    @Column(name = "quantidade_recebida", nullable = false, precision = 15, scale = 4)
    private BigDecimal quantidadeRecebida;

    @NotNull
    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal;

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
