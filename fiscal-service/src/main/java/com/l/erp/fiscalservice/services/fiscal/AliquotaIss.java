package com.l.erp.fiscalservice.services.fiscal;

import java.math.BigDecimal;

/**
 * Alíquota de ISS (legado) do município do local da prestação, para um item da LC 116.
 *
 * <p>{@code referenciaNacional} diz de onde ela veio: {@code false} = o município publicou
 * alíquota própria para o item; {@code true} = caiu na linha-base de referência — aqui o TETO
 * constitucional de 5% (LC 116 art. 8-A), não uma alíquota publicada por órgão, mas o mesmo
 * papel de fallback de {@link AliquotaIbs#referenciaNacional()}.
 */
public record AliquotaIss(BigDecimal aliquotaPct, boolean referenciaNacional) {}
