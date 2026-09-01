package com.l.erp.fiscalservice.services.fiscal;

import java.math.BigDecimal;

/**
 * Alíquota e piso de dispensa de um tributo retido na fonte (fatia 3e — IRRF, CSRF, INSS).
 *
 * <p>{@code valorMinimoBase} é o piso ABAIXO do qual não há retenção — comparado com o valor
 * RETIDO calculado para IRRF (Lei 13.137/2015, art. 67: dispensa se retido &lt; R$10,00) e com o
 * valor BRUTO da operação para CSRF (IN RFB 1234/2012, art. 4º: dispensa se bruto ≤ R$5.000,00).
 * A comparação exata (bruto x retido) é decisão do chamador em {@code MotorFiscalService}, não
 * deste record.
 */
public record AliquotaRetencao(BigDecimal aliquotaPct, BigDecimal valorMinimoBase) {}
