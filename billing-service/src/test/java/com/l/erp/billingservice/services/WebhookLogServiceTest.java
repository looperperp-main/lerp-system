package com.l.erp.billingservice.services;

import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.domain.WebhookLog;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import com.l.erp.billingservice.repository.WebhookLogRepository;
import com.l.erp.common.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookLogServiceTest {

    @Mock
    WebhookLogRepository webhookLogRepository;

    @Mock
    SubscriptionRepository subscriptionRepository;

    @InjectMocks
    WebhookLogService service;

    @Test
    void logReceived_duplicateEventId_returnsExistingWithoutSaving() {
        WebhookLog existing = new WebhookLog();
        when(webhookLogRepository.findByAsaasEventId("evt_1")).thenReturn(Optional.of(existing));

        WebhookLog result = service.logReceived("PAYMENT_RECEIVED", "evt_1", "pay_1", "sub_1", "{}");

        assertThat(result).isSameAs(existing);
        verify(webhookLogRepository, never()).save(any());
    }

    @Test
    void logReceived_newEvent_resolvesTenantIdFromSubscription() {
        when(webhookLogRepository.findByAsaasEventId("evt_2")).thenReturn(Optional.empty());
        Subscription sub = new Subscription();
        sub.setTenantId(42L);
        when(subscriptionRepository.findByAsaasSubscriptionId("sub_1")).thenReturn(Optional.of(sub));
        when(webhookLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WebhookLog result = service.logReceived("PAYMENT_RECEIVED", "evt_2", "pay_1", "sub_1", "{}");

        assertThat(result.getTenantId()).isEqualTo(42L);
        assertThat(result.getStatus()).isEqualTo(Constants.WEBHOOK_RECEBIDO);
        assertThat(result.getAsaasEventId()).isEqualTo("evt_2");
        assertThat(result.getReceivedAt()).isNotNull();
    }

    @Test
    void markProcessed_setsStatusAndTimestamp() {
        WebhookLog wl = new WebhookLog();

        service.markProcessed(wl);

        assertThat(wl.getStatus()).isEqualTo(Constants.WEBHOOK_PROCESSADO);
        assertThat(wl.getProcessedAt()).isNotNull();
        verify(webhookLogRepository).save(wl);
    }
}