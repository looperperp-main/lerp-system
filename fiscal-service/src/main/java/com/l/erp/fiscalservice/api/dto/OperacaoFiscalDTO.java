package com.l.erp.fiscalservice.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado do cálculo fiscal de uma operação (§1.4.10). Sem persistência nesta fatia.
 * {@code memoriaCalculo} é a memória de cálculo citável, regra por regra.
 *
 * <p>{@code NON_NULL}: com o split payment desligado, {@code valorSplitIbs/valorSplitCbs} são
 * {@code null} e não saem no JSON — o documento fiscal não carrega esses campos.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OperacaoFiscalDTO {

    private BigDecimal baseCalculo;
    private BigDecimal valorIs;
    private BigDecimal valorIbsEstadual;
    private BigDecimal valorIbsMunicipal;
    private BigDecimal valorIbs;
    private BigDecimal valorCbs;
    private BigDecimal valorSplitIbs;
    private BigDecimal valorSplitCbs;
    // Legado da transição (fatia 3c) — ICMS em produto OU ISS em serviço, nunca os dois; null
    // quando pctRemanescente = 0 (regime permanente) ou fora do escopo (early-return de zerado()).
    private BigDecimal valorIcms;
    private BigDecimal valorIss;
    // Retenção na fonte (fatia 3e) — valores retidos, não guias/títulos (isso é do AR). Todos
    // null quando a flag de retenção correspondente não foi declarada no request.
    private BigDecimal valorIssRetido;
    private BigDecimal valorIrrf;
    private BigDecimal valorCsrf;
    private BigDecimal valorInss;
    // Crédito de entrada (item 4) — só preenchido quando o CFOP é de ENTRADA; null em saída.
    // Quem persiste saldo/aproveitamento é o operacoes-service (AP); aqui é só o valor calculado.
    private BigDecimal valorCreditoIbs;
    private BigDecimal valorCreditoCbs;
    private String regimeAplicado;
    private List<String> memoriaCalculo;
}