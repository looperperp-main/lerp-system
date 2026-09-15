package com.l.erp.operacoesservice.domain.compras;

import com.l.erp.operacoesservice.domain.compras.enumerators.StatusRecebimentoMercadoria;
import com.l.erp.operacoesservice.domain.compras.enumerators.TipoDocumentoFiscal;
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
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Recebimento de mercadoria de um pedido de compra (spec/p2p-compras.md
 * §"recebimento_mercadoria", Fase 3). Um pedido pode ter N recebimentos (entrega parcial). Dados
 * da NF de entrada digitados manualmente — importação de XML é módulo fiscal, fora de escopo.
 */
@Getter
@Setter
@Entity
@Table(name = "recebimento_mercadoria", schema = "compras")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecebimentoMercadoria extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "numero", nullable = false)
    private Long numero;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private PedidoCompra pedido;

    // Default = deposito do pedido, editável; obrigatório só se houver item MERCADORIA (RN-P2P-11).
    @Column(name = "deposito_id")
    private UUID depositoId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusRecebimentoMercadoria status;

    @NotNull
    @Column(name = "data_recebimento", nullable = false)
    private LocalDate dataRecebimento;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento_fiscal", nullable = false, length = 5)
    private TipoDocumentoFiscal tipoDocumentoFiscal;

    @NotNull
    @Size(max = 20)
    @Column(name = "nfe_numero", nullable = false, length = 20)
    private String nfeNumero;

    // Obrigatória só quando tipoDocumentoFiscal = NFE — NFS-e não tem série (RN-P2P-07).
    @Size(max = 5)
    @Column(name = "nfe_serie", length = 5)
    private String nfeSerie;

    // Obrigatória só quando tipoDocumentoFiscal = NFE; única por tenant quando não nula.
    @Size(max = 44)
    @Column(name = "nfe_chave", length = 44)
    private String nfeChave;

    // Obrigatório só quando tipoDocumentoFiscal = NFSE; único por tenant quando não nulo.
    @Size(max = 50)
    @Column(name = "nfse_codigo_verificacao", length = 50)
    private String nfseCodigoVerificacao;

    @NotNull
    @Column(name = "nfe_data_emissao", nullable = false)
    private LocalDate nfeDataEmissao;

    @NotNull
    @Column(name = "valor_total_nf", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotalNf;

    // Ref. cadastro-service (CondicaoPagamento) — default = do pedido, editável, sem FK física.
    @NotNull
    @Column(name = "condicao_pagamento_id", nullable = false)
    private UUID condicaoPagamentoId;

    // Zerados/informativos no MVP — só o fiscal-service preenche no futuro (decisão do usuário).
    @NotNull
    @ColumnDefault("0")
    @Column(name = "impostos_ibs", nullable = false, precision = 15, scale = 2)
    private BigDecimal impostosIbs;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "impostos_cbs", nullable = false, precision = 15, scale = 2)
    private BigDecimal impostosCbs;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "impostos_is", nullable = false, precision = 15, scale = 2)
    private BigDecimal impostosIs;

    // Preenchido quando nfe.entrada.aprovada for publicado — Fase 4 (faturamento), fora de escopo.
    @Column(name = "faturado_em")
    private Instant faturadoEm;

    @Size(max = 500)
    @Column(name = "observacao", length = 500)
    private String observacao;

    @Size(max = 500)
    @Column(name = "motivo_cancelamento", length = 500)
    private String motivoCancelamento;

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
