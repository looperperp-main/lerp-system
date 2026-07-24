package com.l.erp.fiscalservice.api.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entrada do motor fiscal para UMA operação (por item, não por NF — §1.4.6).
 * Campos conforme Fin.md §1.4.10.
 */
@Getter
@Builder
public class MotorFiscalRequest {

    private String cfop;
    private String ncm;                    // null para serviços
    private String codigoServico;          // código LC 116 — null para produtos
    private String ibgeDestino;            // município do destinatário
    private String ibgeLocalPrestacao;     // apenas para serviços (NFS-e)
    private BigDecimal valorOperacao;
    private LocalDate dataCompetencia;
    private String regimeEmpresa;          // regime do emitente (ver Constants.REGIME_*)
    private String origemProduto;          // 'NACIONAL' | 'ESTRANGEIRO' | 'ZFM'
    private Boolean splitPaymentAplicavel; // da condicao_pagamento
    private String tipoDocumento;          // 'NFe' | 'NFCe' | 'NFSe' | 'CTe'
}