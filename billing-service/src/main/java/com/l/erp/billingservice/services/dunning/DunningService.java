package com.l.erp.billingservice.services.dunning;

import com.l.erp.billingservice.domain.Commission;
import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.domain.SubscriptionStatus;
import com.l.erp.billingservice.infra.config.DunningProperties;
import com.l.erp.billingservice.infra.kafka.KafkaBillingProducerService;
import com.l.erp.billingservice.infra.redis.TenantStatusCacheService;
import com.l.erp.billingservice.repository.CommissionRepository;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Lógica de dunning (Fase 7 — spec §27.7). Age por comparação de timestamps absolutos gravados
 * pelo {@link com.l.erp.billingservice.services.webhook.handler.PaymentOverdueHandler}:
 *
 * <ol>
 *   <li>lembrete quando {@code suspend_at - reminderBeforeDays <= now} e {@code reminder_sent_at} nulo;</li>
 *   <li>suspensão quando {@code suspend_at <= now} (→ SUSPENSO + cache + evento);</li>
 *   <li>cancelamento quando {@code cancel_at <= now} (→ CANCELADO + comissões PENDENTE canceladas + evento).</li>
 * </ol>
 *
 * Idempotente: checa o estado atual antes de transicionar e o reminder só dispara uma vez.
 * A reativação (zerar suspend_at/cancel_at em PAYMENT_RECEIVED) já é feita no PaymentReceivedHandler.
 */
@Service
public class DunningService {

    private static final Logger log = LoggerFactory.getLogger(DunningService.class);
    private static final String PENDENTE = "PENDENTE";
    private static final String CANCELADO = "CANCELADO";

    private final SubscriptionRepository subscriptionRepository;
    private final CommissionRepository commissionRepository;
    private final TenantStatusCacheService tenantStatusCache;
    private final KafkaBillingProducerService kafkaProducer;
    private final DunningProperties dunningProps;

    public DunningService(SubscriptionRepository subscriptionRepository,
                          CommissionRepository commissionRepository,
                          TenantStatusCacheService tenantStatusCache,
                          KafkaBillingProducerService kafkaProducer,
                          DunningProperties dunningProps) {
        this.subscriptionRepository = subscriptionRepository;
        this.commissionRepository = commissionRepository;
        this.tenantStatusCache = tenantStatusCache;
        this.kafkaProducer = kafkaProducer;
        this.dunningProps = dunningProps;
    }

    @Transactional
    public void run() {
        OffsetDateTime now = OffsetDateTime.now();
        enviarLembretes(now);
        suspender(now);
        cancelar(now);
        finalizarCancelamentos(now);
    }

    private void enviarLembretes(OffsetDateTime now) {
        OffsetDateTime limit = now.plusDays(dunningProps.getReminderBeforeDays());
        List<Subscription> pendentes = subscriptionRepository.findRemindersDue(SubscriptionStatus.ATIVA, now, limit);
        for (Subscription sub : pendentes) {
            sub.setReminderSentAt(now);
            sub.setUpdatedAt(now);
            subscriptionRepository.save(sub);
            // ponytail: e-mail de lembrete sai pelo serviço de e-mail existente quando for plugado;
            // o estado (reminder_sent_at) já garante o "uma vez só".
            log.info("Lembrete de dunning — tenant {} suspendAt={}", sub.getTenantId(), sub.getSuspendAt());
        }
    }

    private void suspender(OffsetDateTime now) {
        List<Subscription> aSuspender = subscriptionRepository
                .findByStatusAndSuspendAtLessThanEqual(SubscriptionStatus.ATIVA, now);
        for (Subscription sub : aSuspender) {
            sub.setStatus(SubscriptionStatus.SUSPENSO);
            sub.setSuspendedAt(now);
            sub.setUpdatedAt(now);
            subscriptionRepository.save(sub);
            tenantStatusCache.put(sub.getTenantId(), SubscriptionStatus.SUSPENSO);
            kafkaProducer.sendSubscriptionSuspended(sub.getTenantId());
            log.warn("Assinatura SUSPENSA por inadimplência — tenant {}", sub.getTenantId());
        }
    }

    private void cancelar(OffsetDateTime now) {
        List<Subscription> aCancelar = subscriptionRepository.findByStatusInAndCancelAtLessThanEqual(
                List.of(SubscriptionStatus.ATIVA, SubscriptionStatus.SUSPENSO), now);
        for (Subscription sub : aCancelar) {
            aplicarCancelamento(sub, now, "inadimplência");
        }
    }

    /** Finaliza cancelamentos manuais (§6): CANCELAMENTO_SOLICITADO cujo período pago já venceu. */
    private void finalizarCancelamentos(OffsetDateTime now) {
        List<Subscription> aFinalizar = subscriptionRepository
                .findByStatusAndNextDueDateLessThanEqual(SubscriptionStatus.CANCELAMENTO_SOLICITADO, now);
        for (Subscription sub : aFinalizar) {
            aplicarCancelamento(sub, now, "fim do período pago");
        }
    }

    private void aplicarCancelamento(Subscription sub, OffsetDateTime now, String motivo) {
        sub.setStatus(SubscriptionStatus.CANCELADO);
        sub.setCancelledAt(now);
        sub.setUpdatedAt(now);
        subscriptionRepository.save(sub);

        // Comissões PENDENTE do tenant são canceladas (sem repasse retroativo).
        List<Commission> pendentes = commissionRepository.findByTenantIdAndStatus(sub.getTenantId(), PENDENTE);
        for (Commission c : pendentes) {
            c.setStatus(CANCELADO);
        }
        commissionRepository.saveAll(pendentes);

        tenantStatusCache.put(sub.getTenantId(), SubscriptionStatus.CANCELADO);
        kafkaProducer.sendSubscriptionCancelled(sub.getTenantId());
        log.warn("Assinatura CANCELADA ({}) — tenant {} ({} comissões PENDENTE canceladas)",
                motivo, sub.getTenantId(), pendentes.size());
    }
}
