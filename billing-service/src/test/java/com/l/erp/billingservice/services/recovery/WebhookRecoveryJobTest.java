package com.l.erp.billingservice.services.recovery;

import com.l.erp.billingservice.domain.WebhookLog;
import com.l.erp.billingservice.infra.asaas.dto.AsaasWebhookPayload;
import com.l.erp.billingservice.infra.redis.DistributedLockService;
import com.l.erp.billingservice.repository.WebhookLogRepository;
import com.l.erp.billingservice.services.webhook.WebhookProcessor;
import com.l.erp.common.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookRecoveryJobTest {

    @Mock DistributedLockService lockService;
    @Mock WebhookLogRepository webhookLogRepository;
    @Mock WebhookProcessor webhookProcessor;

    @Test
    void reprocessaWebhookPreso() {
        WebhookRecoveryJob job = new WebhookRecoveryJob(lockService, webhookLogRepository, webhookProcessor);
        when(lockService.acquire(anyString(), anyString(), anyLong())).thenReturn(true);

        WebhookLog wl = new WebhookLog();
        wl.setStatus(Constants.WEBHOOK_RECEBIDO);
        wl.setPayload("{\"event\":\"PAYMENT_RECEIVED\",\"payment\":{\"id\":\"pay_1\",\"subscription\":\"sub_1\",\"status\":\"RECEIVED\",\"value\":99.90}}");
        when(webhookLogRepository.findByStatusAndReceivedAtBefore(eq(Constants.WEBHOOK_RECEBIDO), any()))
                .thenReturn(List.of(wl));

        job.run();

        verify(webhookProcessor).processAsync(any(AsaasWebhookPayload.class), eq(wl));
        verify(lockService).release(anyString(), anyString());
    }

    @Test
    void semLock_naoFaz() {
        WebhookRecoveryJob job = new WebhookRecoveryJob(lockService, webhookLogRepository, webhookProcessor);
        when(lockService.acquire(anyString(), anyString(), anyLong())).thenReturn(false);

        job.run();

        verifyNoInteractions(webhookProcessor);
        verify(webhookLogRepository, never()).findByStatusAndReceivedAtBefore(anyString(), any());
    }
}
