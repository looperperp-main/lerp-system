package com.l.erp.billingservice.services.payout;

import com.l.erp.billingservice.infra.redis.DistributedLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Cron de repasse de comissões (Fase 6 — spec §10.1). Roda D+1 do mês e processa as comissões
 * PENDENTE do mês anterior. Lock distribuído (Redis) garante execução única em deploy multi-instância.
 */
@Component
public class CommissionPayoutJob {

    private static final Logger log = LoggerFactory.getLogger(CommissionPayoutJob.class);

    private final DistributedLockService lockService;
    private final CommissionPayoutService payoutService;

    public CommissionPayoutJob(DistributedLockService lockService, CommissionPayoutService payoutService) {
        this.lockService = lockService;
        this.payoutService = payoutService;
    }

    @Scheduled(cron = "${billing.cron.commission-payout}")
    public void run() {
        YearMonth period = YearMonth.now().minusMonths(1);
        String lockKey = "syax:billing:lock:commission-payout:" + period;
        String lockOwner = UUID.randomUUID().toString();

        if (!lockService.acquire(lockKey, lockOwner, 1800)) {
            log.info("Lock de payout já adquirido por outra instância — pulando período {}", period);
            return;
        }
        try {
            log.info("Iniciando repasse de comissões — período {}", period);
            payoutService.processPayouts(period);
        } finally {
            lockService.release(lockKey, lockOwner);
        }
    }
}
