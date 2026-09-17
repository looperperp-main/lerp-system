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

import java.time.Instant;
import java.util.UUID;

/**
 * Ficha técnica de produção própria (spec/modulos/estoque/estoque.md §12, D11, Fase 2): cabeçalho por produto
 * acabado; os componentes ficam em {@link FichaTecnicaItem} (mesmo padrão de referência plana por UUID, sem
 * FK física, já usado em {@link EstoqueSaldo}/{@link MovimentoEstoque}). Só uma ficha ativa por produto
 * acabado — {@link com.l.erp.operacoesservice.services.estoque.ProducaoService#criarFichaTecnica} desativa a anterior.
 */
@Getter
@Setter
@Entity
@Table(name = "ficha_tecnica", schema = "estoque")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FichaTecnica extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "produto_acabado_id", nullable = false)
    private UUID produtoAcabadoId;

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
