package com.l.erp.fiscalservice.services.fiscal;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * Regime diferenciado por NCM/serviço (§1.4.4). A redução é sempre de ALÍQUOTA,
 * nunca de base (§1.4.2 Passo 5). Subconjunto do enum canônico do §1.8.5 —
 * ampliar (ANEXO_*, ISENTO, IMUNE, ZFM) na fatia com tabela {@code fiscal.regime_dif_ncm}.
 */
public enum RegimeDiferenciado {

    PADRAO(BigDecimal.ZERO, false, false),
    CESTA_BASICA(new BigDecimal("100"), true, false),   // alíquota zero
    REDUCAO_60(new BigDecimal("60"), false, false),
    REDUCAO_30(new BigDecimal("30"), false, false),
    MONOFASICO(BigDecimal.ZERO, false, true);

    private final BigDecimal reducaoPercentual;
    @Getter
    private final boolean cestaBasica;
    @Getter
    private final boolean monofasico;

    RegimeDiferenciado(BigDecimal reducaoPercentual, boolean cestaBasica, boolean monofasico) {
        this.reducaoPercentual = reducaoPercentual;
        this.cestaBasica = cestaBasica;
        this.monofasico = monofasico;
    }

    public BigDecimal reducaoPercentual() {
        return reducaoPercentual;
    }

}