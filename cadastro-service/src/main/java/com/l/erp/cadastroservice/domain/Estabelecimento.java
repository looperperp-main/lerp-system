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
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

/**
 * Matriz/filial de uma Pessoa PJ (spec/estabelecimentos-filiais.md §4.1). Uma pessoa PJ tem
 * sempre 1 matriz (ordem "0001", is_matriz=true) e 0+ filiais. proprio=true marca a matriz que
 * representa a própria empresa do tenant (emitente das notas fiscais).
 */
@Getter
@Setter
@Entity
@Table(name = "estabelecimento", schema = "cadastros")
public class Estabelecimento extends BaseTenantEntity {
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pessoa_id", nullable = false)
    private Pessoa pessoa;

    @NotNull
    @Size(max = 18)
    @Column(name = "cnpj_completo", nullable = false, length = 18)
    private String cnpjCompleto;

    @NotNull
    @Size(max = 4)
    @Column(name = "ordem", nullable = false, length = 4)
    private String ordem;

    @NotNull
    @Column(name = "is_matriz", nullable = false)
    private Boolean matriz;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "proprio", nullable = false)
    private Boolean proprio;

    @Size(max = 20)
    @Column(name = "ie", length = 20)
    private String ie;

    @Size(max = 20)
    @Column(name = "im", length = 20)
    private String im;

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
