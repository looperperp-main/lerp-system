package com.l.erp.operacoesservice.domain.compras;

import com.l.erp.operacoesservice.repository.filter.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Item da requisição — só editável enquanto a requisição está em RASCUNHO (regra de serviço, Fase 1b). */
@Getter
@Setter
@Entity
@Table(name = "requisicao_compra_item", schema = "compras")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RequisicaoCompraItem extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requisicao_id", nullable = false)
    private RequisicaoCompra requisicao;

    // Ref. cadastro-service (cadastros.produto) — sem FK física, valida via API.
    @NotNull
    @Column(name = "produto_id", nullable = false)
    private UUID produtoId;

    @NotNull
    @DecimalMin(value = "0.0001")
    @Column(name = "quantidade", precision = 15, scale = 4, nullable = false)
    private BigDecimal quantidade;

    @Size(max = 255)
    @Column(name = "observacao", length = 255)
    private String observacao;

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
