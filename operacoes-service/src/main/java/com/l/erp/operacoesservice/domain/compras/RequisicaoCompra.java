package com.l.erp.operacoesservice.domain.compras;

import com.l.erp.operacoesservice.domain.compras.enumerators.StatusRequisicaoCompra;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Documento inicial do P2P. Máquina de estados (Fase 1b): RASCUNHO → PENDENTE_APROVACAO →
 * APROVADA → [EM_COTACAO] → ATENDIDA, com REPROVADA/CANCELADA como saídas.
 * spec/p2p-compras.md §"requisicao_compra"
 */
@Getter
@Setter
@Entity
@Table(name = "requisicao_compra", schema = "compras")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RequisicaoCompra extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "numero", nullable = false)
    private Long numero;

    @NotNull
    @Column(name = "solicitante_id", nullable = false)
    private UUID solicitanteId;

    // Ref. cadastro-service (cadastros.deposito) — sem FK física; obrigatório só se
    // houver item de mercadoria na requisição (validação de serviço, Fase 1b).
    @Column(name = "deposito_id")
    private UUID depositoId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    private StatusRequisicaoCompra status;

    @Size(max = 500)
    @Column(name = "justificativa", length = 500)
    private String justificativa;

    @Column(name = "data_necessidade")
    private LocalDate dataNecessidade;

    @Column(name = "aprovador_id")
    private UUID aprovadorId;

    @Column(name = "aprovado_em")
    private Instant aprovadoEm;

    @Size(max = 500)
    @Column(name = "motivo_reprovacao", length = 500)
    private String motivoReprovacao;

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
