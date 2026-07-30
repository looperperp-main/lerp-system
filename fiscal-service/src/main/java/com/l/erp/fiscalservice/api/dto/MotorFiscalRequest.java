package com.l.erp.fiscalservice.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entrada do motor fiscal para UMA operação (por item, não por NF — §1.4.6).
 * Campos conforme Fin.md §1.4.10.
 *
 * <p>Bean validation cobre só os campos sempre-presentes. O par ncm × codigoServico é
 * mutuamente exclusivo (produto vs serviço) e é validado no {@code MotorFiscalService}, junto
 * das demais regras fiscais, devolvendo código {@code FISCAL_*} em vez de mensagem de campo.
 */
@Getter
@Setter                 // Jackson desserializa via no-arg + setters (Jackson 3 / tools.jackson)
@Builder
@NoArgsConstructor      // exigido pelo Jackson; @Builder sozinho não deixa construtor utilizável
@AllArgsConstructor     // @Builder precisa do all-args (some ao adicionar @NoArgsConstructor)
public class MotorFiscalRequest {

    @NotBlank
    private String cfop;
    private String ncm;                    // null para serviços
    private String codigoServico;          // código LC 116 — null para produtos
    // Classificação tributária IBS/CBS do serviço (Anexo VIII). @JsonProperty porque o getter
    // Lombok é getCClassTrib() e o Jackson batizaria o campo de 'cclassTrib' — o nome no JSON
    // tem que ser o mesmo da tag do XML da NFS-e.
    @JsonProperty("cClassTrib")
    private String cClassTrib;             // obrigatório quando codigoServico vem preenchido
    private String ibgeDestino;            // município do destinatário
    private String ibgeLocalPrestacao;     // apenas para serviços (NFS-e)
    @NotNull
    @Positive
    private BigDecimal valorOperacao;
    // Componentes da base (LC 214 art. 12, §2º) — todos OPCIONAIS: quem não manda continua tendo
    // o valor da operação como base. Frete, seguro e acessórias ENTRAM na base; desconto
    // incondicional SAI. Ficam separados porque a NF-e exige os campos individualizados e porque
    // sem eles a memória de cálculo não é auditável até a origem.
    @PositiveOrZero
    private BigDecimal valorDesconto;      // apenas desconto INCONDICIONAL (condicional não reduz base)
    @PositiveOrZero
    private BigDecimal valorFrete;
    @PositiveOrZero
    private BigDecimal valorSeguro;
    @PositiveOrZero
    private BigDecimal valorOutrasDespesas;
    @NotNull
    private LocalDate dataCompetencia;
    @NotBlank
    private String regimeEmpresa;          // regime do emitente (ver Constants.REGIME_*)
    private String origemProduto;          // 'NACIONAL' | 'ESTRANGEIRO' | 'ZFM'
    private Boolean splitPaymentAplicavel; // da condicao_pagamento
    private String tipoDocumento;          // 'NFe' | 'NFCe' | 'NFSe' | 'CTe'
}