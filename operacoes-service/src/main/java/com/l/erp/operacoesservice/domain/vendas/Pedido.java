package com.l.erp.operacoesservice.domain.vendas;

import com.l.erp.operacoesservice.domain.vendas.enumerators.ModalidadeFrete;
import com.l.erp.operacoesservice.domain.vendas.enumerators.StatusPedido;
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
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "pedido", schema = "vendas")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Pedido extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "numero", nullable = false)
    private Long numero;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusPedido status;

    @NotNull
    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(name = "vendedor_id")
    private UUID vendedorId;

    @Column(name = "condicao_pagamento_id")
    private UUID condicaoPagamentoId;

    @Column(name = "transportadora_id")
    private UUID transportadoraId;

    @Column(name = "deposito_id")
    private UUID depositoId;

    @NotNull
    @ColumnDefault("SEM_FRETE")
    @Enumerated(EnumType.STRING)
    @Column(name = "modalidade_frete", nullable = false, length = 10)
    private ModalidadeFrete modalidadeFrete;

    @Column(name = "valor_frete", precision = 15, scale = 2)
    private BigDecimal valorFrete;

    @NotNull
    @Column(name = "valor_itens", precision = 15, scale = 2, nullable = false)
    private BigDecimal valorItens;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "valor_desconto", precision = 15, scale = 2, nullable = false)
    private BigDecimal valorDesconto;

    @NotNull
    @Column(name = "valor_total", precision = 15, scale = 2, nullable = false)
    private BigDecimal valorTotal;

    @NotNull
    @Column(name = "data_emissao", nullable = false)
    private LocalDate dataEmissao;

    @Column(name = "data_validade")
    private LocalDate dataValidade;

    @Column(name = "data_confirmacao")
    private Instant dataConfirmacao;

    @Column(name = "data_expedicao")
    private Instant dataExpedicao;

    @Column(name = "data_faturamento")
    private Instant dataFaturamento;

    @Column(name = "data_cancelamento")
    private Instant dataCancelamento;

    @Size(max = 500)
    @Column(name = "motivo_cancelamento", length = 500)
    private String motivoCancelamento;

    // D4: preenchidos só no faturamento, a partir do agregado de POST /fiscal/calcular por item
    // (fiscal-service) — nulos em ORCAMENTO/CONFIRMADO/EXPEDIDO. spec/o2c-vendas.md §8.
    @Column(name = "valor_total_nf", precision = 15, scale = 2)
    private BigDecimal valorTotalNf;

    @Column(name = "valor_ibs", precision = 15, scale = 2)
    private BigDecimal valorIbs;

    @Column(name = "valor_cbs", precision = 15, scale = 2)
    private BigDecimal valorCbs;

    @Column(name = "valor_is", precision = 15, scale = 2)
    private BigDecimal valorIs;

    @Column(name = "valor_iss", precision = 15, scale = 2)
    private BigDecimal valorIss;

    @Column(name = "valor_retencoes", precision = 15, scale = 2)
    private BigDecimal valorRetencoes;

    @Size(max = 1000)
    @Column(name = "observacao", length = 1000)
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
