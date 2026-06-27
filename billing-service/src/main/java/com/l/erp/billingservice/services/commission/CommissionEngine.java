package com.l.erp.billingservice.services.commission;

import com.l.erp.billingservice.domain.Commission;
import com.l.erp.billingservice.domain.CommissionModel;
import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.repository.CommissionRepository;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra a geração da comissão. Alimentado pela trilha Kafka existente
 * ({@code partner.commission.calculated} → {@code PartnerCommissionCalculatedConsumer}), não pelo
 * webhook de pagamento — o billing é responsável só por pagamentos e pela comunicação com o Asaas;
 * o cálculo da taxa fica no partner-service, que detém o referral.
 *
 * <p>Idempotência em duas camadas: pré-check por {@code asaas_payment_id} (descarte limpo) e a
 * constraint UNIQUE {@code uq_commission_asaas_payment} (barreira contra corrida).</p>
 */
@Service
public class CommissionEngine {

    private static final Logger log = LoggerFactory.getLogger(CommissionEngine.class);

    private final CommissionRepository commissionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CommissionStrategyFactory strategyFactory;

    public CommissionEngine(CommissionRepository commissionRepository,
                            SubscriptionRepository subscriptionRepository,
                            CommissionStrategyFactory strategyFactory) {
        this.commissionRepository = commissionRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.strategyFactory = strategyFactory;
    }

    @Transactional
    public void generate(CommissionGenerationCommand command) {
        if (command.asaasPaymentId() != null && !command.asaasPaymentId().isBlank()
                && commissionRepository.existsByAsaasPaymentId(command.asaasPaymentId())) {
            log.info("Comissão para asaasPaymentId={} já existe — idempotência, ignorando", command.asaasPaymentId());
            return;
        }

        Subscription subscription = subscriptionRepository
                .findByAsaasSubscriptionId(command.asaasSubscriptionId())
                .orElse(null);
        if (subscription == null) {
            log.warn("Subscription asaasSubscriptionId={} não encontrada — comissão não gerada",
                    command.asaasSubscriptionId());
            return;
        }

        // Só existe o modelo RECORRENTE hoje; a factory mantém a porta aberta para ANUAL no futuro.
        CommissionStrategy strategy = strategyFactory.getStrategy(CommissionModel.RECORRENTE);
        Commission commission = strategy.build(command, subscription);

        try {
            commissionRepository.save(commission);
            log.info("Comissão {} gerada — partner={} tenant={} amount={} period={}",
                    strategy.getModel(), command.partnerId(), command.tenantId(),
                    commission.getAmount(), commission.getPeriod());
        } catch (DataIntegrityViolationException _) {
            log.info("Comissão duplicada ignorada (UNIQUE asaas_payment_id={})", command.asaasPaymentId());
        }
    }
}