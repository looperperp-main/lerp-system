package com.l.erp.fiscalservice.services.fiscal;

import java.math.BigDecimal;

/**
 * Alíquota IBS decomposta em parcela estadual e municipal (percentuais).
 *
 * <p>{@code referenciaNacional} diz de onde ela veio: {@code false} = o município publicou alíquota
 * própria; {@code true} = caiu na linha-base de referência do Senado
 * ({@code Constants.FISCAL_IBGE_REFERENCIA_NACIONAL}). O motor avisa no segundo caso — a referência
 * é a alíquota legal de quem não legislou, mas se o ente legislou e a carga não tem, o imposto sai
 * errado, e isso não pode ser silencioso.
 */
public record AliquotaIbs(BigDecimal estadual, BigDecimal municipal, boolean referenciaNacional) {}
