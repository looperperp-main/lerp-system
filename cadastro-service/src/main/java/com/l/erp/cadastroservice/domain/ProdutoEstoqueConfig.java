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
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "produto_estoque_config", schema = "cadastros")
public class ProdutoEstoqueConfig extends BaseTenantEntity {
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    // AQUI: Mapeando o Depósito que estava faltando e causou o erro NOT NULL
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deposito_id", nullable = false)
    private Deposito deposito;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fornecedor_preferencial_id")
    private ProdutoFornecedor fornecedorPreferencial;

    @Column(name = "estoque_minimo", precision = 15, scale = 4)
    private BigDecimal estoqueMinimo;

    @Column(name = "estoque_maximo", precision = 15, scale = 4)
    private BigDecimal estoqueMaximo;

    @Column(name = "ponto_reposicao", precision = 15, scale = 4)
    private BigDecimal pontoReposicao;

    @Column(name = "lead_time_dias")
    private Integer leadTimeDias;

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