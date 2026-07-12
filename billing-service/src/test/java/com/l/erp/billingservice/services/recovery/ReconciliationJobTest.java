package com.l.erp.billingservice.services.recovery;

import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.domain.SubscriptionStatus;
import com.l.erp.billingservice.infra.redis.DistributedLockService;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import com.l.erp.billingservice.services.JobExecutionRecorder;
import com.l.erp.billingservice.services.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationJobTest {

    @Mock DistributedLockService lockService;
    @Mock SubscriptionRepository subscriptionRepository;
    @Mock SubscriptionService subscriptionService;
    @Mock JobExecutionRecorder recorder;

    private Subscription aguardando(long tenantId, String asaasSubId) {
        Subscription s = new Subscription();
        s.setTenantId(tenantId);
        s.setStatus(SubscriptionStatus.AGUARDANDO_PAGAMENTO);
        s.setAsaasSubscriptionId(asaasSubId);
        return s;
    }

    private void runRecorder() {
        doAnswer(invocation -> {
            Runnable work = invocation.getArgument(1);
            work.run();
            return null;
        }).when(recorder).record(anyString(), any());
    }

    @Test
    void pendenteComAsaasSubscriptionId_reprocessaViaService() {
        runRecorder();
        ReconciliationJob job = new ReconciliationJob(lockService, subscriptionRepository, subscriptionService, recorder);
        when(lockService.acquire(anyString(), anyString(), anyLong())).thenReturn(true);
        Subscription sub = aguardando(5L, "sub_x");
        when(subscriptionRepository.findByStatus(SubscriptionStatus.AGUARDANDO_PAGAMENTO))
                .thenReturn(List.of(sub));

        job.run();

        verify(subscriptionService).reprocessarPagamento(sub);
        verify(lockService).release(anyString(), anyString());
    }

    @Test
    void semAsaasSubscriptionId_naoReprocessa() {
        runRecorder();
        ReconciliationJob job = new ReconciliationJob(lockService, subscriptionRepository, subscriptionService, recorder);
        when(lockService.acquire(anyString(), anyString(), anyLong())).thenReturn(true);
        Subscription sub = aguardando(5L, null);
        when(subscriptionRepository.findByStatus(SubscriptionStatus.AGUARDANDO_PAGAMENTO))
                .thenReturn(List.of(sub));

        job.run();

        verify(subscriptionService, never()).reprocessarPagamento(any());
    }

    @Test
    void semLock_naoFaz() {
        ReconciliationJob job = new ReconciliationJob(lockService, subscriptionRepository, subscriptionService, recorder);
        when(lockService.acquire(anyString(), anyString(), anyLong())).thenReturn(false);

        job.run();

        verifyNoInteractions(subscriptionService, subscriptionRepository, recorder);
    }
}
