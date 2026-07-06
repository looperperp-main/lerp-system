package com.l.erp.billingservice.services.recovery;

import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.domain.SubscriptionStatus;
import com.l.erp.billingservice.infra.redis.DistributedLockService;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import com.l.erp.billingservice.services.JobExecutionRecorder;
import com.l.erp.billingservice.services.SubscriptionService;
import com.l.erp.common.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Reconciliação (Fase 7 — spec §27.x): pagamentos confirmados no Asaas cujo webhook não chegou.
 * Varre as assinaturas {@code AGUARDANDO_PAGAMENTO} e delega ao
 * {@link SubscriptionService#reprocessarPagamento} — mesma lógica do botão "Reprocessar" do admin.
 *
 * <p>Idempotente: ao ativar, a assinatura sai de {@code AGUARDANDO_PAGAMENTO}, então a próxima
 * execução não a reprocessa; a comissão é guardada por {@code asaas_payment_id} no engine.</p>
 */
@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final DistributedLockService lockService;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final JobExecutionRecorder recorder;

    public ReconciliationJob(DistributedLockService lockService,
                             SubscriptionRepository subscriptionRepository,
                             SubscriptionService subscriptionService,
                             JobExecutionRecorder recorder) {
        this.lockService = lockService;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
        this.recorder = recorder;
    }

    @Scheduled(cron = "${billing.cron.reconciliation}")
    public void run() {
        String lockKey = "syax:billing:lock:reconciliation";
        String lockOwner = UUID.randomUUID().toString();
        if (!lockService.acquire(lockKey, lockOwner, 600)) {
            log.info("Lock de reconciliation já adquirido — pulando");
            return;
        }
        try {
            recorder.record(Constants.JOB_KEY_RECONCILIATION, () -> {
                List<Subscription> pendentes = subscriptionRepository.findByStatus(SubscriptionStatus.AGUARDANDO_PAGAMENTO);
                for (Subscription sub : pendentes) {
                    try {
                        if (sub.getAsaasSubscriptionId() != null) {
                            subscriptionService.reprocessarPagamento(sub);
                        }
                    } catch (Exception e) {
                        log.error("Falha na reconciliação — subscription Asaas {}", sub.getAsaasSubscriptionId(), e);
                    }
                }
            });
        } finally {
            lockService.release(lockKey, lockOwner);
        }
    }
}
