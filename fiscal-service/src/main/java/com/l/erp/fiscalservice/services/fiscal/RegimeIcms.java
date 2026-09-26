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
 * @param pFcp percentual de FCP embutido na linha (issue #103) — desmembrado do {@code aliqNominal}
 *     nas UFs que o incluíam na alíquota interna cheia (RJ, SE); {@code ZERO} nas demais.
 */
public record RegimeIcms(BigDecimal aliqNominal, BigDecimal pReducaoBase, boolean ncmGenerico, BigDecimal pFcp) {
}
