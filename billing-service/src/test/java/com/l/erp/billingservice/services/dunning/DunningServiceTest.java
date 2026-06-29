package com.l.erp.billingservice.services.dunning;

import com.l.erp.billingservice.domain.Commission;
import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.domain.SubscriptionStatus;
import com.l.erp.billingservice.infra.config.DunningProperties;
import com.l.erp.billingservice.infra.kafka.KafkaBillingProducerService;
import com.l.erp.billingservice.infra.redis.TenantStatusCacheService;
import com.l.erp.billingservice.repository.CommissionRepository;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DunningServiceTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock CommissionRepository commissionRepository;
    @Mock TenantStatusCacheService tenantStatusCache;
    @Mock KafkaBillingProducerService kafkaProducer;

    DunningService service;

    @BeforeEach
    void setUp() {
        service = new DunningService(subscriptionRepository, commissionRepository,
                tenantStatusCache, kafkaProducer, new DunningProperties());
        // por padrão, nada nas três janelas (cada teste sobrescreve o que precisa)
        lenient().when(subscriptionRepository.findRemindersDue(any(), any(), any())).thenReturn(List.of());
        lenient().when(subscriptionRepository.findByStatusAndSuspendAtLessThanEqual(any(), any())).thenReturn(List.of());
        lenient().when(subscriptionRepository.findByStatusInAndCancelAtLessThanEqual(any(), any())).thenReturn(List.of());
    }

    private Subscription sub(long tenantId, String status) {
        Subscription s = new Subscription();
        s.setTenantId(tenantId);
        s.setStatus(status);
        return s;
    }

    @Test
    void lembrete_setaReminderSentAt_eSalva() {
        Subscription s = sub(1L, SubscriptionStatus.ATIVA);
        s.setSuspendAt(OffsetDateTime.now().plusDays(1));
        when(subscriptionRepository.findRemindersDue(eq(SubscriptionStatus.ATIVA), any(), any()))
                .thenReturn(List.of(s));

        service.run();

        assertThat(s.getReminderSentAt()).isNotNull();
        verify(subscriptionRepository).save(s);
        verify(kafkaProducer, never()).sendSubscriptionSuspended(anyLong());
        verify(kafkaProducer, never()).sendSubscriptionCancelled(anyLong());
    }

    @Test
    void suspensao_marcaSUSPENSO_cache_eEvento() {
        Subscription s = sub(7L, SubscriptionStatus.ATIVA);
        when(subscriptionRepository.findByStatusAndSuspendAtLessThanEqual(eq(SubscriptionStatus.ATIVA), any()))
                .thenReturn(List.of(s));

        service.run();

        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.SUSPENSO);
        verify(tenantStatusCache).put(7L, SubscriptionStatus.SUSPENSO);
        verify(kafkaProducer).sendSubscriptionSuspended(7L);
    }

    @Test
    void cancelamento_marcaCANCELADO_cancelaComissoesPendentes_eEvento() {
        Subscription s = sub(9L, SubscriptionStatus.SUSPENSO);
        when(subscriptionRepository.findByStatusInAndCancelAtLessThanEqual(anyList(), any()))
                .thenReturn(List.of(s));
        Commission c = new Commission();
        c.setTenantId(9L);
        c.setStatus("PENDENTE");
        when(commissionRepository.findByTenantIdAndStatus(9L, "PENDENTE")).thenReturn(List.of(c));

        service.run();

        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.CANCELADO);
        assertThat(c.getStatus()).isEqualTo("CANCELADO");
        ArgumentCaptor<List<Commission>> captor = ArgumentCaptor.forClass(List.class);
        verify(commissionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(c);
        verify(tenantStatusCache).put(9L, SubscriptionStatus.CANCELADO);
        verify(kafkaProducer).sendSubscriptionCancelled(9L);
    }
}
