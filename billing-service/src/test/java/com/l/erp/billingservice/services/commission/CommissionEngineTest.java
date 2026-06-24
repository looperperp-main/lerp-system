package com.l.erp.billingservice.services.commission;

import com.l.erp.billingservice.domain.Commission;
import com.l.erp.billingservice.domain.CommissionModel;
import com.l.erp.billingservice.domain.CommissionStatus;
import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.repository.CommissionRepository;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CommissionEngineTest {

    @Mock
    CommissionRepository commissionRepository;

    @Mock
    SubscriptionRepository subscriptionRepository;

    @Mock
    CommissionStrategyFactory strategyFactory;

    @InjectMocks
    CommissionEngine engine;

    @Test
    void duplicatePayment_skippedBeforeLookup() {
        when(commissionRepository.existsByAsaasPaymentId("pay_1")).thenReturn(true);

        engine.generate(command());

        verify(subscriptionRepository, never()).findByAsaasSubscriptionId(any());
        verify(commissionRepository, never()).save(any());
    }

    @Test
    void subscriptionNotFound_noCommission() {
        when(commissionRepository.existsByAsaasPaymentId("pay_1")).thenReturn(false);
        when(subscriptionRepository.findByAsaasSubscriptionId("sub_1")).thenReturn(Optional.empty());

        engine.generate(command());

        verify(commissionRepository, never()).save(any());
    }

    @Test
    void happyPath_savesPendingCommission() {
        when(commissionRepository.existsByAsaasPaymentId("pay_1")).thenReturn(false);
        when(subscriptionRepository.findByAsaasSubscriptionId("sub_1")).thenReturn(Optional.of(subscription()));
        when(strategyFactory.getStrategy(CommissionModel.RECORRENTE)).thenReturn(new RecurrentCommissionStrategy());

        engine.generate(command());

        ArgumentCaptor<Commission> captor = ArgumentCaptor.forClass(Commission.class);
        verify(commissionRepository).save(captor.capture());
        Commission saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(CommissionStatus.PENDENTE);
        assertThat(saved.getCommissionModel()).isEqualTo(CommissionModel.RECORRENTE);
        assertThat(saved.getAmount()).isEqualByComparingTo("17.90");
        assertThat(saved.getAsaasPaymentId()).isEqualTo("pay_1");
    }

    @Test
    void raceOnUniqueConstraint_swallowed() {
        when(commissionRepository.existsByAsaasPaymentId("pay_1")).thenReturn(false);
        when(subscriptionRepository.findByAsaasSubscriptionId("sub_1")).thenReturn(Optional.of(subscription()));
        when(strategyFactory.getStrategy(CommissionModel.RECORRENTE)).thenReturn(new RecurrentCommissionStrategy());
        when(commissionRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup asaas_payment_id"));

        // A constraint UNIQUE é a barreira definitiva — uma corrida não pode estourar o consumer Kafka
        assertThatNoException().isThrownBy(() -> engine.generate(command()));
    }

    private static Subscription subscription() {
        Subscription s = new Subscription();
        s.setId(UUID.randomUUID());
        s.setTenantId(7L);
        s.setBillingCycle("MONTHLY");
        s.setValue(new BigDecimal("179.00"));
        return s;
    }

    private static CommissionGenerationCommand command() {
        return new CommissionGenerationCommand(UUID.randomUUID(), 7L, "sub_1", "pay_1", new BigDecimal("17.90"));
    }
}