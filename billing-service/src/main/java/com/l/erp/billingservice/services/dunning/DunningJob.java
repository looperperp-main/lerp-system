package com.l.erp.billingservice.services.dunning;

import com.l.erp.billingservice.infra.redis.DistributedLockService;
import com.l.erp.billingservice.services.JobExecutionRecorder;
import com.l.erp.common.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Cron diário de dunning (Fase 7 — spec §27.7). Aplica lembrete/suspensão/cancelamento por
 * comparação de timestamps. Lock distribuído (Redis) garante execução única multi-instância.
 */
@Component
public class DunningJob {

    private static final Logger log = LoggerFactory.getLogger(DunningJob.class);

    private final DistributedLockService lockService;
    private final DunningService dunningService;
    private final JobExecutionRecorder recorder;

    public DunningJob(DistributedLockService lockService, DunningService dunningService,
                      JobExecutionRecorder recorder) {
        this.lockService = lockService;
        this.dunningService = dunningService;
        this.recorder = recorder;
    }

    @Scheduled(cron = "${billing.cron.dunning}")
    public void run() {
        String lockKey = "syax:billing:lock:dunning:" + LocalDate.now();
        String lockOwner = UUID.randomUUID().toString();

        if (!lockService.acquire(lockKey, lockOwner, 1800)) {
            log.info("Lock de dunning já adquirido por outra instância — pulando");
            return;
        }
        try {
            recorder.record(Constants.JOB_KEY_DUNNING, () -> {
                log.info("Iniciando ciclo de dunning");
                dunningService.run();
            });
        } finally {
            lockService.release(lockKey, lockOwner);
        }
    }
}
