package com.l.erp.operacoesservice.domain.estoque;

import com.l.erp.operacoesservice.domain.estoque.enumerators.StatusOrdemProducao;
import com.l.erp.operacoesservice.repository.filter.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ordem de produção própria (spec/modulos/estoque/estoque.md §12, D11, Fase 2). Apontar produção
 * ({@link com.l.erp.operacoesservice.services.estoque.ProducaoService#apontarProducao}) gera N
 * {@code SAIDA_PRODUCAO} (um por componente da ficha técnica ativa) + 1 {@code ENTRADA_PRODUCAO}.
 */
@Getter
@Setter
@Entity
@Table(name = "ordem_producao", schema = "estoque")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrdemProducao extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "produto_acabado_id", nullable = false)
    private UUID produtoAcabadoId;

    @NotNull
    @Column(name = "deposito_id", nullable = false)
    private UUID depositoId;

    @NotNull
    @Column(name = "quantidade_planejada", precision = 15, scale = 4, nullable = false)
    private BigDecimal quantidadePlanejada;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private StatusOrdemProducao status;

    @Column(name = "concluida_em")
    private Instant concluidaEm;

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
