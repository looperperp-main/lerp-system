package com.l.erp.cadastroservice.domain;

import com.l.erp.cadastroservice.repository.filter.BaseTenantEntity;
import jakarta.persistence.CascadeType;
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
@Table(name = "produto", schema = "cadastros")
public class Produto extends BaseTenantEntity {
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private ProdutoCategoria categoria;

    @Size(max = 80)
    @NotNull
    @Column(name = "sku", nullable = false, length = 80)
    private String sku;

    @Size(max = 80)
    @Column(name = "codigo_externo", length = 80)
    private String codigoExterno;

    @Size(max = 200)
    @NotNull
    @Column(name = "nome", nullable = false, length = 200)
    private String nome;

    @Column(name = "descricao", length = Integer.MAX_VALUE)
    private String descricao;

    @Size(max = 10)
    @NotNull
    @Column(name = "unidade", nullable = false, length = 10)
    private String unidade;

    @Size(max = 10)
    @Column(name = "unidade_secundaria", length = 10)
    private String unidadeSecundaria;

    @Column(name = "fator_conversao", precision = 10, scale = 4)
    private BigDecimal fatorConversao;

    @Size(max = 10)
    @Column(name = "ncm", length = 10)
    private String ncm;

    @Size(max = 14)
    @Column(name = "ean", length = 14)
    private String ean;

    @Size(max = 10)
    @Column(name = "cest", length = 10)
    private String cest;

    @Size(max = 2)
    @Column(name = "origem", length = 2)
    private String origem;

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

    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProdutoEstoqueConfig> produtoEstoqueConfigs = new LinkedHashSet<>();

    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProdutoFornecedor> produtoFornecedors = new LinkedHashSet<>();

    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProdutoPreco> produtoPrecos = new LinkedHashSet<>();


}