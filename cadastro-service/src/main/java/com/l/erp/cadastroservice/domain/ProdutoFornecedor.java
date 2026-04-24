package com.l.erp.cadastroservice.domain;

import com.l.erp.cadastroservice.repository.filter.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "produto_fornecedor", schema = "cadastros")
public class ProdutoFornecedor extends BaseTenantEntity {
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fornecedor_id", nullable = false)
    private Fornecedor fornecedor;

    @Size(max = 80)
    @Column(name = "codigo_produto_fornecedor", length = 80)
    private String codigoProdutoFornecedor;

    @Column(name = "preco_custo", precision = 15, scale = 4)
    private BigDecimal precoCusto;

    @Column(name = "lead_time_dias")
    private Integer leadTimeDias;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "preferencial", nullable = false)
    private Boolean preferencial;

    @NotNull
    @ColumnDefault("true")
    @Column(name = "ativo", nullable = false)
    private Boolean ativo;

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

    @OneToMany(mappedBy = "fornecedorPreferencial")
    private Set<ProdutoEstoqueConfig> produtoEstoqueConfigs = new LinkedHashSet<>();


}