package com.l.erp.billingservice.services.webhook.handler;

import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.domain.SubscriptionStatus;
import com.l.erp.billingservice.infra.asaas.AsaasException;
import com.l.erp.billingservice.infra.asaas.AsaasGateway;
import com.l.erp.billingservice.infra.asaas.dto.AsaasPaymentData;
import com.l.erp.billingservice.infra.asaas.dto.AsaasSubscriptionResponse;
import com.l.erp.billingservice.infra.asaas.dto.AsaasWebhookPayload;
import com.l.erp.billingservice.infra.exception.TransientException;
import com.l.erp.billingservice.infra.kafka.KafkaBillingProducerService;
import com.l.erp.billingservice.infra.redis.TenantStatusCacheService;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReceivedHandlerTest {

    @Mock
    SubscriptionRepository subscriptionRepository;

    @Mock
    KafkaBillingProducerService kafkaProducer;

    @Mock
    TenantStatusCacheService tenantStatusCache;

    @Mock
    AsaasGateway asaasGateway;

    @InjectMocks
    PaymentReceivedHandler handler;

    @Test
    void activatesSubscription_usingAsaasNextDueDate() {
        Subscription sub = subscription(SubscriptionStatus.AGUARDANDO_PAGAMENTO, new BigDecimal("179.00"));
        when(asaasGateway.getSubscription("sub_1"))
                .thenReturn(new AsaasSubscriptionResponse("sub_1", "ACTIVE", LocalDate.of(2026, 7, 24)));
        when(subscriptionRepository.findByAsaasSubscriptionId("sub_1")).thenReturn(Optional.of(sub));

        handler.handle(payload("pay_1", "sub_1", new BigDecimal("179.00")));

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ATIVA);
        assertThat(sub.getActivatedAt()).isNotNull();
        assertThat(sub.getNextDueDate()).isNotNull();
        assertThat(sub.getSuspendAt()).isNull();
        verify(subscriptionRepository).save(sub);
        verify(tenantStatusCache).put(1L, SubscriptionStatus.ATIVA);
        verify(kafkaProducer).sendSubscriptionActivated(
                eq(1L), eq("BASIC"), eq(new BigDecimal("179.00")), eq("sub_1"), eq("pay_1"), any());
    }

    @Test
    void cancelledSubscription_isNotReactivated() {
        Subscription sub = subscription(SubscriptionStatus.CANCELADO, new BigDecimal("179.00"));
        when(asaasGateway.getSubscription("sub_1"))
                .thenReturn(new AsaasSubscriptionResponse("sub_1", "ACTIVE", LocalDate.of(2026, 7, 24)));
        when(subscriptionRepository.findByAsaasSubscriptionId("sub_1")).thenReturn(Optional.of(sub));

        handler.handle(payload("pay_1", "sub_1", new BigDecimal("179.00")));

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELADO);
        verify(subscriptionRepository, never()).save(any());
        verify(kafkaProducer, never()).sendSubscriptionActivated(any(), any(), any(), any(), any(), any());
        verify(tenantStatusCache, never()).put(any(), any());
    }

    @Test
    void divergentValue_stillActivates_andEventUsesReceivedValue() {
        Subscription sub = subscription(SubscriptionStatus.AGUARDANDO_PAGAMENTO, new BigDecimal("179.00"));
        when(asaasGateway.getSubscription("sub_1"))
                .thenReturn(new AsaasSubscriptionResponse("sub_1", "ACTIVE", LocalDate.of(2026, 7, 24)));
        when(subscriptionRepository.findByAsaasSubscriptionId("sub_1")).thenReturn(Optional.of(sub));

        handler.handle(payload("pay_1", "sub_1", new BigDecimal("150.00")));

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ATIVA);
        // §28.8: comissão é calculada sobre o valor REALMENTE recebido
        verify(kafkaProducer).sendSubscriptionActivated(
                eq(1L), eq("BASIC"), eq(new BigDecimal("150.00")), eq("sub_1"), eq("pay_1"), any());
    }

    @Test
    void asaasFailure_isWrappedAsTransient() {
        when(asaasGateway.getSubscription("sub_1")).thenThrow(new AsaasException("Asaas fora do ar"));

        assertThatThrownBy(() -> handler.handle(payload("pay_1", "sub_1", new BigDecimal("179.00"))))
                .isInstanceOf(TransientException.class);

        verify(subscriptionRepository, never()).findByAsaasSubscriptionId(any());
    }

    private static Subscription subscription(String status, BigDecimal value) {
        Subscription s = new Subscription();
        s.setTenantId(1L);
        s.setPlanType("BASIC");
        s.setValue(value);
        s.setStatus(status);
        s.setAsaasSubscriptionId("sub_1");
        s.setCreatedAt(OffsetDateTime.now());
        return s;
    }

    private static AsaasWebhookPayload payload(String paymentId, String subscriptionId, BigDecimal value) {
        AsaasPaymentData payment = new AsaasPaymentData();
        payment.setId(paymentId);
        payment.setSubscription(subscriptionId);
        payment.setValue(value);
        AsaasWebhookPayload p = new AsaasWebhookPayload();
        p.setEvent("PAYMENT_RECEIVED");
        p.setPayment(payment);
        return p;
    }
}