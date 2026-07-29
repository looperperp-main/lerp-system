package com.l.erp.fiscalservice.services.fiscal;

import com.l.erp.common.util.Constants;

import java.math.BigDecimal;

/**
 * Regime diferenciado resolvido para um NCM/serviço — uma linha de {@code fiscal.regime_dif_ncm}
 * (§1.4.4). A redução é sempre de ALÍQUOTA, nunca de base (§1.4.2 Passo 5).
 *
 * <p>Era um enum na Fatia 1. Virou record na fatia com DB porque o catálogo de regimes da
 * LC 214/2025 (ANEXO_I_ZERO … ANEXO_XI_60, ISENTO, IMUNE, ZFM) cresce por migração: com enum,
 * um regime novo no banco e ausente do código cairia calado em PADRAO e cobraria alíquota cheia
 * num item reduzido. Aqui o {@code percentual_reducao} da linha é a fonte autoritativa; o nome
 * viaja junto só para a memória de cálculo e o campo {@code regimeAplicado}.
 *
 * @param name               nome do regime como está no banco (ex. {@code ANEXO_I_ZERO})
 * @param reducaoPercentual  redução de alíquota em pontos percentuais (0 a 100)
 * @param aliquotaZero       redução de 100% — IBS/CBS/IS zerados sem passar pelo cálculo
 * @param monofasico         já recolhido na origem; só a 1ª etapa da cadeia tributa
 */
public record RegimeDiferenciado(String name, BigDecimal reducaoPercentual,
                                 boolean aliquotaZero, boolean monofasico) {

    private static final BigDecimal CEM = new BigDecimal("100");

    /** NCM/serviço sem regime cadastrado: tributação cheia, não bloqueia (MF-10). */
    public static final RegimeDiferenciado PADRAO =
            new RegimeDiferenciado(Constants.REGIME_DIF_PADRAO, BigDecimal.ZERO, false, false);

    /** Constrói a partir de uma linha de {@code fiscal.regime_dif_ncm}. */
    public static RegimeDiferenciado de(String nome, BigDecimal reducaoPercentual) {
        return new RegimeDiferenciado(
                nome,
                reducaoPercentual,
                reducaoPercentual.compareTo(CEM) >= 0,
                Constants.REGIME_DIF_MONOFASICO.equals(nome));
    }
}
