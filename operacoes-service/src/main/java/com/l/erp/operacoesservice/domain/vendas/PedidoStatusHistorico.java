package com.l.erp.operacoesservice.domain.vendas;

import com.l.erp.operacoesservice.domain.vendas.enumerators.StatusPedido;
import com.l.erp.operacoesservice.repository.filter.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only — uma linha por transição (inclusive na criação do orçamento,
 * onde statusDe é null). Sem update/delete. spec/o2c-vendas.md §3.3
 */
@Getter
@Setter
@Entity
@Table(name = "pedido_status_historico", schema = "vendas")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PedidoStatusHistorico extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_de", length = 20)
    private StatusPedido statusDe;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status_para", nullable = false, length = 20)
    private StatusPedido statusPara;

    @Size(max = 500)
    @Column(name = "motivo", length = 500)
    private String motivo;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
