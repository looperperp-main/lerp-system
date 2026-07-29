package com.l.erp.fiscalservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
    private String ibgeDestino;            // município do destinatário
    private String ibgeLocalPrestacao;     // apenas para serviços (NFS-e)
    @NotNull
    @Positive
    private BigDecimal valorOperacao;
    @NotNull
    private LocalDate dataCompetencia;
    @NotBlank
    private String regimeEmpresa;          // regime do emitente (ver Constants.REGIME_*)
    private String origemProduto;          // 'NACIONAL' | 'ESTRANGEIRO' | 'ZFM'
    private Boolean splitPaymentAplicavel; // da condicao_pagamento
    private String tipoDocumento;          // 'NFe' | 'NFCe' | 'NFSe' | 'CTe'
}