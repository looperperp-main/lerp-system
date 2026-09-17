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

import java.math.BigDecimal;
import java.util.UUID;

/** Componente + quantidade de uma {@link FichaTecnica} (spec/modulos/estoque/estoque.md §12, D11, Fase 2). */
@Getter
@Setter
@Entity
@Table(name = "ficha_tecnica_item", schema = "estoque")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FichaTecnicaItem extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "ficha_tecnica_id", nullable = false)
    private UUID fichaTecnicaId;

    @NotNull
    @Column(name = "produto_componente_id", nullable = false)
    private UUID produtoComponenteId;

    @NotNull
    @Column(name = "quantidade", precision = 15, scale = 4, nullable = false)
    private BigDecimal quantidade;
}
