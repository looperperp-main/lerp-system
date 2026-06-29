package com.l.erp.billingservice.services.recovery;

import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.domain.SubscriptionStatus;
import com.l.erp.billingservice.infra.asaas.AsaasGateway;
import com.l.erp.billingservice.infra.asaas.dto.AsaasPaymentResponse;
import com.l.erp.billingservice.infra.asaas.dto.AsaasWebhookPayload;
import com.l.erp.billingservice.infra.redis.DistributedLockService;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import com.l.erp.billingservice.services.webhook.handler.PaymentReceivedHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationJobTest {

    @Mock DistributedLockService lockService;
    @Mock SubscriptionRepository subscriptionRepository;
    @Mock AsaasGateway asaasGateway;
    @Mock PaymentReceivedHandler paymentReceivedHandler;

    private Subscription aguardando(long tenantId, String asaasSubId) {
        Subscription s = new Subscription();
        s.setTenantId(tenantId);
        s.setStatus(SubscriptionStatus.AGUARDANDO_PAGAMENTO);
        s.setAsaasSubscriptionId(asaasSubId);
        return s;
    }

    @Test
    void pagamentoConfirmadoSemWebhook_ativaViaHandler() {
        ReconciliationJob job = new ReconciliationJob(lockService, subscriptionRepository, asaasGateway, paymentReceivedHandler);
        when(lockService.acquire(anyString(), anyString(), anyLong())).thenReturn(true);
        when(subscriptionRepository.findByStatus(SubscriptionStatus.AGUARDANDO_PAGAMENTO))
                .thenReturn(List.of(aguardando(5L, "sub_x")));
        when(asaasGateway.getFirstPayment("sub_x"))
                .thenReturn(new AsaasPaymentResponse("pay_x", "RECEIVED", new BigDecimal("99.90"), null, null, null));

        job.run();

        ArgumentCaptor<AsaasWebhookPayload> captor = ArgumentCaptor.forClass(AsaasWebhookPayload.class);
        verify(paymentReceivedHandler).handle(captor.capture());
        assertThat(captor.getValue().getEvent()).isEqualTo("PAYMENT_RECEIVED");
        assertThat(captor.getValue().getPayment().getId()).isEqualTo("pay_x");
        assertThat(captor.getValue().getPayment().getSubscription()).isEqualTo("sub_x");
    }

    @Test
    void pagamentoNaoPago_naoAtiva() {
        ReconciliationJob job = new ReconciliationJob(lockService, subscriptionRepository, asaasGateway, paymentReceivedHandler);
        when(lockService.acquire(anyString(), anyString(), anyLong())).thenReturn(true);
        when(subscriptionRepository.findByStatus(SubscriptionStatus.AGUARDANDO_PAGAMENTO))
                .thenReturn(List.of(aguardando(5L, "sub_y")));
        when(asaasGateway.getFirstPayment("sub_y"))
                .thenReturn(new AsaasPaymentResponse("pay_y", "PENDING", new BigDecimal("99.90"), null, null, null));

        job.run();

        verify(paymentReceivedHandler, never()).handle(any());
    }
}
