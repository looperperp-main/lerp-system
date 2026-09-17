package com.l.erp.operacoesservice.domain.estoque;

import com.l.erp.operacoesservice.domain.estoque.enumerators.PendenciaTipoEstoque;
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
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

/**
 * Pendência de regularização de saldo negativo (spec/modulos/estoque/estoque.md §12, RN-EST-12). Gerada por
 * {@link com.l.erp.operacoesservice.services.estoque.EstoqueService} quando um movimento sujeito a
 * bloqueio deixa o saldo negativo (REVENDA/USO_CONSUMO/MATERIA_PRIMA com override, ou PRODUTO_ACABADO sempre).
 */
@Getter
@Setter
@Entity
@Table(name = "pendencia_estoque", schema = "estoque")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PendenciaEstoque extends BaseTenantEntity {
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
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private PendenciaTipoEstoque tipo;

    @NotNull
    @Column(name = "movimento_id", nullable = false)
    private UUID movimentoId;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "resolvida", nullable = false)
    private Boolean resolvida;

    @NotNull
    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    @Column(name = "resolvida_em")
    private Instant resolvidaEm;

    @Column(name = "resolvido_por")
    private UUID resolvidoPor;
}
