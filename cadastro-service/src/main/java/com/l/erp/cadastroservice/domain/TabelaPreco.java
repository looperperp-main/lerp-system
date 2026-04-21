package com.l.erp.cadastroservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "tabela_preco", schema = "cadastros")
public class TabelaPreco {
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Size(max = 100)
    @NotNull
    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Size(max = 3)
    @NotNull
    @ColumnDefault("'BRL'")
    @Column(name = "moeda", nullable = false, length = 3)
    private String moeda;

    @NotNull
    @ColumnDefault("true")
    @Column(name = "ativa", nullable = false)
    private Boolean ativa;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "padrao", nullable = false)
    private Boolean padrao;

    @NotNull
    @Column(name = "inicio_vigencia", nullable = false)
    private LocalDate inicioVigencia;

    @Column(name = "fim_vigencia")
    private LocalDate fimVigencia;

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

    @OneToMany(mappedBy = "tabelaPreco")
    private Set<ProdutoPreco> produtoPrecos = new LinkedHashSet<>();

    @OneToMany(mappedBy = "tabelaPreco")
    private Set<TabelaPrecoGrupoCliente> tabelaPrecoGrupoClientes = new LinkedHashSet<>();


}