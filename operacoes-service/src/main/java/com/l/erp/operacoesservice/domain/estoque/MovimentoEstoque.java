package com.l.erp.operacoesservice.domain.estoque;

import com.l.erp.operacoesservice.domain.estoque.enumerators.OrigemMovimentoEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.TipoAjusteEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.TipoMovimentoEstoque;
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
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Livro-razão append-only do estoque (spec/modulos/estoque/estoque.md §3.1) — nunca sofre UPDATE/DELETE;
 * correção é sempre um movimento novo (estorno ou ajuste).
 */
@Getter
@Setter
@Entity
@Table(name = "movimento_estoque", schema = "estoque")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MovimentoEstoque extends BaseTenantEntity {
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
    @Column(name = "tipo", nullable = false, length = 25)
    private TipoMovimentoEstoque tipo;

    @NotNull
    @Column(name = "quantidade", precision = 15, scale = 4, nullable = false)
    private BigDecimal quantidade;

    @Column(name = "valor_unitario", precision = 15, scale = 4)
    private BigDecimal valorUnitario;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "origem_tipo", nullable = false, length = 20)
    private OrigemMovimentoEstoque origemTipo;

    @Column(name = "origem_id")
    private UUID origemId;

    // [D9, §12] superado como campo obrigatório por tipoAjuste — vira observação livre
    // complementar, opcional mesmo em AJUSTE/INVENTARIO.
    @Size(max = 500)
    @Column(name = "motivo", length = 500)
    private String motivo;

    // [D9, RN-EST-11, §12] obrigatório em AJUSTE/INVENTARIO; substitui motivo como campo mandatório.
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_ajuste", length = 30)
    private TipoAjusteEstoque tipoAjuste;

    // [D9, §12] opcional — laudo, B.O., termo de descarte (lastro documental do ajuste).
    @Size(max = 200)
    @Column(name = "documento_referencia", length = 200)
    private String documentoReferencia;

    // [D7, RN-EST-10, §12] obrigatório quando tipo=SAIDA_CONSUMO; null nos demais tipos.
    @Column(name = "centro_custo_id")
    private UUID centroCustoId;

    @NotNull
    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @NotNull
    @Column(name = "ocorrido_em", nullable = false)
    private Instant ocorridoEm;

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
