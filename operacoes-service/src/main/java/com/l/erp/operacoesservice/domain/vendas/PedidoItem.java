package com.l.erp.operacoesservice.domain.vendas;

import com.l.erp.operacoesservice.domain.vendas.enumerators.TipoItemPedido;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "pedido_item", schema = "vendas")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PedidoItem extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @NotNull
    @Column(name = "produto_id", nullable = false)
    private UUID produtoId;

    // Snapshot de Produto.tipo no momento em que o item foi adicionado — imutável.
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_item", nullable = false, length = 12)
    private TipoItemPedido tipoItem;

    @NotNull
    @Column(name = "quantidade", precision = 15, scale = 4, nullable = false)
    private BigDecimal quantidade;

    @NotNull
    @Column(name = "preco_unitario", precision = 15, scale = 2, nullable = false)
    private BigDecimal precoUnitario;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "desconto", precision = 15, scale = 2, nullable = false)
    private BigDecimal desconto;

    @NotNull
    @Column(name = "valor_total", precision = 15, scale = 2, nullable = false)
    private BigDecimal valorTotal;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "preco_manual", nullable = false)
    private Boolean precoManual;

    @Column(name = "preco_tabela", precision = 15, scale = 2)
    private BigDecimal precoTabela;

    @Column(name = "tabela_preco_id")
    private UUID tabelaPrecoId;

    @Size(max = 10)
    @Column(name = "origem_preco", length = 10)
    private String origemPreco;

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
