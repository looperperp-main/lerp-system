package com.l.erp.cadastroservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;import jakarta.persistence.GeneratedValue;import jakarta.persistence.GenerationType;import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;import jakarta.persistence.ManyToOne;import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "endereco", schema = "cadastros")
public class Endereco {
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pessoa_id", nullable = false)
    private Pessoa pessoa;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Size(max = 20)
    @NotNull
    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo;

    @Size(max = 200)
    @NotNull
    @Column(name = "logradouro", nullable = false, length = 200)
    private String logradouro;

    @Size(max = 20)
    @Column(name = "numero", length = 20)
    private String numero;

    @Size(max = 100)
    @Column(name = "complemento", length = 100)
    private String complemento;

    @Size(max = 100)
    @Column(name = "bairro", length = 100)
    private String bairro;

    @Size(max = 100)
    @NotNull
    @Column(name = "cidade", nullable = false, length = 100)
    private String cidade;

    @Size(max = 2)
    @NotNull
    @Column(name = "uf", nullable = false, length = 2)
    private String uf;

    @Size(max = 9)
    @NotNull
    @Column(name = "cep", nullable = false, length = 9)
    private String cep;

    @Size(max = 10)
    @Column(name = "ibge_codigo", length = 10)
    private String ibgeCodigo;

    @Size(max = 60)
    @ColumnDefault("'Brasil'")
    @Column(name = "pais", length = 60)
    private String pais;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "principal", nullable = false)
    private Boolean principal;

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