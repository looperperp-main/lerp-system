package com.l.erp.fiscalservice.services.fiscal;

/**
 * Dados fiscais de um CFOP (§1.4.2 Passo 1 e §1.4.3 Passo 9).
 * {@code primeiraEtapaCadeia} identifica produção própria/importação — usado no
 * tratamento monofásico (§1.4.2 Passo 2): só a 1ª etapa recolhe.
 */
public record CfopInfo(
        String cfop,
        TipoOperacaoFiscal tipoOperacao,
        boolean geraCreditoIbs,
        boolean geraCreditoCbs,
        boolean primeiraEtapaCadeia
) {}