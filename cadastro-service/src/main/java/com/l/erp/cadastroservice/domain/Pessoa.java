package com.l.erp.cadastroservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "pessoa", schema = "cadastros")
public class Pessoa {
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Size(max = 2)
    @NotNull
    @Column(name = "tipo", nullable = false, length = 2)
    private String tipo;

    @Size(max = 200)
    @NotNull
    @Column(name = "nome_razao", nullable = false, length = 200)
    private String nomeRazao;

    @Size(max = 200)
    @Column(name = "apelido_fantasia", length = 200)
    private String apelidoFantasia;

    @Size(max = 18)
    @NotNull
    @Column(name = "documento", nullable = false, length = 18)
    private String documento;

    @Size(max = 20)
    @Column(name = "ie", length = 20)
    private String ie;

    @Size(max = 20)
    @Column(name = "im", length = 20)
    private String im;

    @Size(max = 20)
    @Column(name = "rg", length = 20)
    private String rg;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Size(max = 200)
    @Column(name = "email", length = 200)
    private String email;

    @Size(max = 20)
    @Column(name = "telefone", length = 20)
    private String telefone;

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


}