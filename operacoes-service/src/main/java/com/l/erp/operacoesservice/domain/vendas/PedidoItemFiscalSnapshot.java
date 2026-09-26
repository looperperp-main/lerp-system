package com.l.erp.operacoesservice.domain.vendas;

import com.l.erp.operacoesservice.repository.filter.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot imutável do resultado fiscal de um item de pedido, gravado no momento do faturamento
 * (espelha {@code OperacaoFiscalDTO} do fiscal-service, sem recálculo) — decisão registrada em
 * spec/modulos/emissao-fiscal/emissao-fiscal.md §3 item 11 (opção b). Alimenta a Etapa 2+ da
 * emissão fiscal (geração do XML), que não pode recalcular o que já foi faturado.
 *
 * <p>Append-only: nunca há {@code UPDATE} numa linha existente. Correção pós-rejeição da SEFAZ
 * grava nova linha com {@code versao} incrementada e {@code motivoCorrecao} preenchido — ação
 * manual da tela fiscal do emissao-fiscal-service (ainda não implementada; fora do escopo desta
 * mudança, que só grava a versão 1 no faturamento).
 *
 * <p>ponytail: {@code cfop} resolvido não está aqui porque o fiscal-service não o devolve em
 * {@code OperacaoFiscalDTO} hoje (só existe na mutação interna de {@code MotorFiscalRequest}) —
 * upgrade quando isso virar campo de resposta. {@code memoriaCalculo} (lista de strings) também
 * fica de fora: é memória de auditoria do cálculo, não dado que o XML da NF-e consome.
 */
@Getter
@Setter
@Entity
@Table(name = "pedido_item_fiscal_snapshot", schema = "vendas")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PedidoItemFiscalSnapshot extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_item_id", nullable = false)
    private PedidoItem pedidoItem;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "versao", nullable = false)
    private Integer versao;

    @Column(name = "motivo_correcao", length = 500)
    private String motivoCorrecao;

    @Column(name = "base_calculo", precision = 15, scale = 2)
    private BigDecimal baseCalculo;

    @Column(name = "valor_ibs_estadual", precision = 15, scale = 2)
    private BigDecimal valorIbsEstadual;

    @Column(name = "valor_ibs_municipal", precision = 15, scale = 2)
    private BigDecimal valorIbsMunicipal;

    @Column(name = "valor_ibs", precision = 15, scale = 2)
    private BigDecimal valorIbs;

    @Column(name = "valor_cbs", precision = 15, scale = 2)
    private BigDecimal valorCbs;

    @Column(name = "valor_split_ibs", precision = 15, scale = 2)
    private BigDecimal valorSplitIbs;

    @Column(name = "valor_split_cbs", precision = 15, scale = 2)
    private BigDecimal valorSplitCbs;

    @Column(name = "valor_icms", precision = 15, scale = 2)
    private BigDecimal valorIcms;

    @Column(name = "valor_is", precision = 15, scale = 2)
    private BigDecimal valorIs;

    @Column(name = "valor_iss", precision = 15, scale = 2)
    private BigDecimal valorIss;

    @Column(name = "valor_iss_retido", precision = 15, scale = 2)
    private BigDecimal valorIssRetido;

    @Column(name = "valor_irrf", precision = 15, scale = 2)
    private BigDecimal valorIrrf;

    @Column(name = "valor_csrf", precision = 15, scale = 2)
    private BigDecimal valorCsrf;

    @Column(name = "valor_inss", precision = 15, scale = 2)
    private BigDecimal valorInss;

    @Column(name = "valor_credito_ibs", precision = 15, scale = 2)
    private BigDecimal valorCreditoIbs;

    @Column(name = "valor_credito_cbs", precision = 15, scale = 2)
    private BigDecimal valorCreditoCbs;

    @Column(name = "regime_aplicado", length = 30)
    private String regimeAplicado;

    @Column(name = "c_class_trib", length = 10)
    private String cClassTrib;

    @Column(name = "percentual_ibs_uf", precision = 7, scale = 4)
    private BigDecimal percentualIbsUf;

    @Column(name = "percentual_ibs_municipal", precision = 7, scale = 4)
    private BigDecimal percentualIbsMunicipal;

    @Column(name = "percentual_cbs", precision = 7, scale = 4)
    private BigDecimal percentualCbs;

    @Column(name = "percentual_reducao_aplicado", precision = 7, scale = 4)
    private BigDecimal percentualReducaoAplicado;

    @Column(name = "cst", length = 3)
    private String cst;

    @Column(name = "cst_icms", length = 3)
    private String cstIcms;

    @Column(name = "csosn", length = 3)
    private String csosn;

    @Column(name = "percentual_icms_nominal", precision = 7, scale = 4)
    private BigDecimal percentualIcmsNominal;

    @Column(name = "percentual_reducao_base_icms", precision = 7, scale = 4)
    private BigDecimal percentualReducaoBaseIcms;

    @Column(name = "modalidade_base_calculo_icms", length = 2)
    private String modalidadeBaseCalculoIcms;

    @Column(name = "percentual_fcp", precision = 7, scale = 4)
    private BigDecimal percentualFcp;

    @Column(name = "valor_fcp", precision = 15, scale = 2)
    private BigDecimal valorFcp;

    @Column(name = "percentual_icms_interestadual", precision = 7, scale = 4)
    private BigDecimal percentualIcmsInterestadual;

    @Column(name = "base_calculo_uf_destino", precision = 15, scale = 2)
    private BigDecimal baseCalculoUfDestino;

    @Column(name = "base_calculo_fcp_uf_destino", precision = 15, scale = 2)
    private BigDecimal baseCalculoFcpUfDestino;

    @Column(name = "percentual_icms_uf_destino", precision = 7, scale = 4)
    private BigDecimal percentualIcmsUfDestino;

    @Column(name = "percentual_fcp_uf_destino", precision = 7, scale = 4)
    private BigDecimal percentualFcpUfDestino;

    @Column(name = "percentual_partilha_destino", precision = 7, scale = 4)
    private BigDecimal percentualPartilhaDestino;

    @Column(name = "valor_icms_uf_destino", precision = 15, scale = 2)
    private BigDecimal valorIcmsUfDestino;

    @Column(name = "valor_fcp_uf_destino", precision = 15, scale = 2)
    private BigDecimal valorFcpUfDestino;

    @Column(name = "valor_icms_uf_remetente", precision = 15, scale = 2)
    private BigDecimal valorIcmsUfRemetente;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
