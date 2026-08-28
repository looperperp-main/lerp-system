package com.l.erp.fiscalservice.services.fiscal;

import java.math.BigDecimal;

/**
 * Linha de {@code fiscal.aliquota_regime_tributo} (item 7.7): override do regime que
 * {@link RegimeDiferenciado#reducaoPercentual()} não consegue expressar sozinho — redução
 * isolada por tributo ({@code tributo} = {@code Constants.FISCAL_TRIBUTO_IBS}/{@code _CBS}) ou
 * alíquota somada em valor absoluto ({@code tributo} = {@code Constants.FISCAL_TRIBUTO_TOTAL}).
 *
 * @param tributo IBS, CBS ou TOTAL (soma dos dois) — {@code Constants.FISCAL_TRIBUTO_*}.
 * @param tipo    PERCENTUAL_REDUCAO (mesma semântica do {@code fator} default) ou
 *                ALIQUOTA_ABSOLUTA (substitui a alíquota de referência, não a reduz) —
 *                {@code Constants.FISCAL_TIPO_*}.
 * @param valor   percentual de redução (0-100) ou alíquota absoluta em %, conforme {@code tipo}.
 */
public record RegimeTributoOverride(String tributo, String tipo, BigDecimal valor) {
}
