package com.l.erp.operacoesservice.domain.estoque;

import com.l.erp.operacoesservice.repository.filter.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Saldo materializado por produto+depósito (spec/estoque.md §3.3) — atualizado via upsert com
 * SELECT ... FOR UPDATE na mesma transação do movimento que o originou.
 */
@Getter
@Setter
@Entity
@Table(name = "estoque_saldo", schema = "estoque")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EstoqueSaldo extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "produto_id", nullable = false)
    private UUID produtoId;

    @NotNull
    @Column(name = "deposito_id", nullable = false)
    private UUID depositoId;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "quantidade", precision = 15, scale = 4, nullable = false)
    private BigDecimal quantidade;

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
