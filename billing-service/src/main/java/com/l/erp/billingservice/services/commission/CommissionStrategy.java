package com.l.erp.billingservice.services.commission;

import com.l.erp.billingservice.domain.Commission;
import com.l.erp.billingservice.domain.Subscription;

/**
 * Monta a {@link Commission} a partir do comando e da subscription, conforme o modelo de comissão.
 *
 * <p>Mantida como interface para que um modelo futuro (ANUAL, por volume, etc.) seja adicionado sem
 * tocar no {@link CommissionEngine}. Hoje só há {@link RecurrentCommissionStrategy}.</p>
 */
public interface CommissionStrategy {

    /** Modelo que esta strategy atende — chave do {@link CommissionStrategyFactory}. */
    String getModel();

    Commission build(CommissionGenerationCommand command, Subscription subscription);
}