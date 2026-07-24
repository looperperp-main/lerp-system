package com.l.erp.fiscalservice.api.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado do cálculo fiscal de uma operação (§1.4.10). Sem persistência nesta fatia.
 * {@code memoriaCalculo} é a memória de cálculo citável, regra por regra.
 */
@Getter
@Builder
public class OperacaoFiscalDTO {

    private BigDecimal baseCalculo;
    private BigDecimal valorIs;
    private BigDecimal valorIbsEstadual;
    private BigDecimal valorIbsMunicipal;
    private BigDecimal valorIbs;
    private BigDecimal valorCbs;
    private BigDecimal valorSplitIbs;
    private BigDecimal valorSplitCbs;
    private String regimeAplicado;
    private List<String> memoriaCalculo;
}