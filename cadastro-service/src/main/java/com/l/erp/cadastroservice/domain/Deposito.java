package com.l.erp.cadastroservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "deposito", schema = "cadastros")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Deposito {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Size(max = 100)
    @NotNull
    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Size(max = 500)
    @Column(name = "descricao", length = 500)
    private String descricao;

    @Size(max = 30)
    @Column(name = "tipo", length = 30)
    private String tipo;

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