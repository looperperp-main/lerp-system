package com.l.erp.fiscalservice.services.fiscal;

import com.l.erp.common.util.Constants;

import java.math.BigDecimal;

/**
 * Alíquota interestadual de ICMS (Resolução do Senado 22/89 + 13/2012) — regra FIXA sobre a lista
 * de UF e a origem do produto, não dado que muda (comentário de fiscal-schema-011.yaml). Fecha o
 * achado 2.2 (issue #102) e é pré-requisito da Fase B de DIFAL (issue #103,
 * spec/fiscal/pis-cofins-difal-calculo.md §5.3).
 *
 * <p>ponytail: origem ESTRANGEIRO ⇒ 4% ignora a nuance do conteúdo de importação ≤ 40% (origens
 * 3/5/8 da NF-e) — {@code origemProduto} hoje não distingue isso. Upgrade quando o cadastro de
 * produto trouxer a origem de 0 a 8.
 */
public final class AliquotaInterestadual {

    private AliquotaInterestadual() {
    }

    public static BigDecimal de(String ufOrigem, String ufDestino, String origemProduto) {
        if (Constants.FISCAL_ORIGEM_ESTRANGEIRO.equals(origemProduto)) {
            return Constants.FISCAL_ICMS_INTERESTADUAL_IMPORTADO;
        }
        boolean origemSulSudeste = Constants.FISCAL_UF_SUL_SUDESTE_SEM_ES.contains(ufOrigem);
        boolean destinoForaSulSudeste = !Constants.FISCAL_UF_SUL_SUDESTE_SEM_ES.contains(ufDestino);
        return origemSulSudeste && destinoForaSulSudeste
                ? Constants.FISCAL_ICMS_INTERESTADUAL_REDUZIDA
                : Constants.FISCAL_ICMS_INTERESTADUAL_GERAL;
    }
}
