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

import java.time.Instant;
import java.util.UUID;

/**
 * Fechamento de período de estoque (spec/modulos/estoque/estoque.md §12, RN-EST-13): não fecha com pendência
 * aberta nem saldo negativo em nenhum produto/depósito do tenant — negativo é tolerado intra-mês, nunca
 * atravessa um fechamento.
 */
@Getter
@Setter
@Entity
@Table(name = "fechamento_estoque", schema = "estoque")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FechamentoEstoque extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "competencia", nullable = false, length = 7)
    private String competencia;

    @NotNull
    @Column(name = "data_fechamento", nullable = false)
    private Instant dataFechamento;

    @NotNull
    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;
}
