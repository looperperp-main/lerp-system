package com.l.erp.operacoesservice.domain.compras;

import com.l.erp.operacoesservice.domain.compras.enumerators.StatusCotacaoCompra;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Cotação de compra multi-fornecedor (spec/p2p-compras.md §"cotacao_compra", Fase 5). */
@Getter
@Setter
@Entity
@Table(name = "cotacao_compra", schema = "compras")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CotacaoCompra extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "numero", nullable = false)
    private Long numero;

    // Origem: cotação pode ser avulsa (requisicao_id nulo).
    @Column(name = "requisicao_id")
    private UUID requisicaoId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusCotacaoCompra status;

    @Column(name = "data_limite_resposta")
    private LocalDate dataLimiteResposta;

    // Ref. cadastro-service (Deposito) — sem FK física; herdado da requisição quando houver.
    @NotNull
    @Column(name = "deposito_id", nullable = false)
    private UUID depositoId;

    // Setado no encerramento com a resposta vencedora escolhida pelo usuário.
    @Column(name = "cotacao_fornecedor_vencedor_id")
    private UUID cotacaoFornecedorVencedorId;

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
