package com.l.erp.migracaoservice.services.imports;

import java.util.UUID;

/**
 * Grava as linhas VALID de um batch no cadastro de destino. Um por entidade: entidade nova do import é um
 * bean novo, sem mexer no ImportPromotionService. Roda DENTRO da transação da promoção (tudo-ou-nada, spec §11):
 * qualquer exceção desfaz o batch inteiro.
 */
public interface Promotor {

    /** O entity_type que este promotor atende (Constants.MIGRACAO_ENTIDADE_*). */
    String entidade();

    void promover(Long tenantId, UUID userId, UUID batchId);
}
