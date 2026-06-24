package com.l.erp.billingservice.services;

import com.l.erp.billingservice.api.dto.CheckoutRequest;
import com.l.erp.billingservice.api.dto.CheckoutResponse;
import com.l.erp.billingservice.domain.Plan;
import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.domain.SubscriptionStatus;
import com.l.erp.billingservice.infra.asaas.AsaasException;
import com.l.erp.billingservice.infra.asaas.AsaasGateway;
import com.l.erp.billingservice.infra.asaas.dto.AsaasPaymentResponse;
import com.l.erp.billingservice.infra.asaas.dto.AsaasPixQrCodeResponse;
import com.l.erp.billingservice.infra.asaas.dto.AsaasSubscriptionResponse;
import com.l.erp.billingservice.repository.PlanRepository;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock
    PlanRepository planRepository;

    @Mock
    SubscriptionRepository subscriptionRepository;

    @Mock
    AsaasGateway asaasGateway;

    @InjectMocks
    CheckoutService service;

    private static final CheckoutRequest REQUEST =
            new CheckoutRequest("STARTER", "12345678000199", "tenant@x.com", "Tenant X LTDA");

    @Test
    void planNotFound_returns404_withoutCallingAsaas() {
        when(planRepository.findByPlanTypeAndActiveTrue("STARTER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createCheckout(1L, REQUEST))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));

        verifyNoInteractions(asaasGateway);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void happyPath_persistsAguardandoPagamento_andReturnsBoletoAndPix() {
        stubPlanAndAsaas();
        when(asaasGateway.getPixQrCode("pay_1"))
                .thenReturn(new AsaasPixQrCodeResponse(true, "base64img", "pix-copia-cola"));

        CheckoutResponse response = service.createCheckout(1L, REQUEST);

        assertThat(response.paymentUrl()).isEqualTo("https://asaas/invoice");
        assertThat(response.boletoUrl()).isEqualTo("https://asaas/boleto.pdf");
        assertThat(response.pixQrCode()).isEqualTo("base64img");
        assertThat(response.pixCopyPaste()).isEqualTo("pix-copia-cola");
        assertThat(response.planType()).isEqualTo("STARTER");
        assertThat(response.value()).isEqualByComparingTo("99.90");

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        Subscription saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.AGUARDANDO_PAGAMENTO);
        assertThat(saved.getAsaasSubscriptionId()).isEqualTo("sub_1");
        assertThat(saved.getTenantId()).isEqualTo(1L);
    }

    @Test
    void pixUnavailable_doesNotBreakCheckout() {
        stubPlanAndAsaas();
        when(asaasGateway.getPixQrCode("pay_1")).thenThrow(new AsaasException("PIX ainda não gerado"));

        CheckoutResponse response = service.createCheckout(1L, REQUEST);

        assertThat(response.boletoUrl()).isEqualTo("https://asaas/boleto.pdf");
        assertThat(response.pixQrCode()).isNull();
        assertThat(response.pixCopyPaste()).isNull();
    }

    private void stubPlanAndAsaas() {
        when(planRepository.findByPlanTypeAndActiveTrue("STARTER")).thenReturn(Optional.of(plan()));
        when(asaasGateway.createCustomer(any())).thenReturn("cus_1");
        when(asaasGateway.createSubscription(any()))
                .thenReturn(new AsaasSubscriptionResponse("sub_1", "ACTIVE", LocalDate.of(2026, 7, 24)));
        when(asaasGateway.getFirstPayment("sub_1")).thenReturn(new AsaasPaymentResponse(
                "pay_1", "PENDING", new BigDecimal("99.90"), LocalDate.of(2026, 6, 25),
                "https://asaas/invoice", "https://asaas/boleto.pdf"));
    }

    private static Plan plan() {
        Plan p = new Plan();
        p.setName("Plano Starter");
        p.setPlanType("STARTER");
        p.setBillingCycle("MONTHLY");
        p.setValue(new BigDecimal("99.90"));
        p.setActive(true);
        p.setCreatedAt(OffsetDateTime.now());
        p.setCreatedBy("test");
        return p;
    }
}