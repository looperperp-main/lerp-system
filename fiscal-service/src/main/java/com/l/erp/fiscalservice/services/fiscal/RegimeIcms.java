package com.l.erp.fiscalservice.services.fiscal;

import java.math.BigDecimal;

/**
 * Alíquota de ICMS interno resolvida por {@code fiscal.matriz_tributaria} (fatia 3b):
 * {@code aliqNominal} e {@code pReducaoBase} separados — o motor calcula a efetiva
 * multiplicando os dois, igual ao {@code vBC}/{@code pICMS} da NF-e.
 *
 * @param ncmGenerico true quando a linha casou pelo NCM/NBS de fallback
 *     ({@code Constants.FISCAL_NCM_NBS_FALLBACK}), não por um código específico — mesmo
 *     princípio de {@link AliquotaIss#referenciaNacional}.
 */
public record RegimeIcms(BigDecimal aliqNominal, BigDecimal pReducaoBase, boolean ncmGenerico) {
}
