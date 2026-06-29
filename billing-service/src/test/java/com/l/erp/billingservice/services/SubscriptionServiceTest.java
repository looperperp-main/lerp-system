package com.l.erp.billingservice.services;

import com.l.erp.billingservice.api.dto.CancelSubscriptionResponse;
import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.domain.SubscriptionStatus;
import com.l.erp.billingservice.infra.asaas.AsaasGateway;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock AsaasGateway asaasGateway;

    private Subscription sub(String status, String asaasId) {
        Subscription s = new Subscription();
        s.setTenantId(1L);
        s.setStatus(status);
        s.setAsaasSubscriptionId(asaasId);
        s.setNextDueDate(OffsetDateTime.now().plusDays(20));
        return s;
    }

    @Test
    void cancela_chamaAsaas_eMarcaCancelamentoSolicitado() {
        SubscriptionService service = new SubscriptionService(subscriptionRepository, asaasGateway);
        Subscription s = sub(SubscriptionStatus.ATIVA, "sub_1");
        when(subscriptionRepository.findByTenantId(1L)).thenReturn(List.of(s));

        CancelSubscriptionResponse resp = service.cancelForTenant(1L);

        verify(asaasGateway).cancelSubscription("sub_1");
        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.CANCELAMENTO_SOLICITADO);
        assertThat(resp.status()).isEqualTo(SubscriptionStatus.CANCELAMENTO_SOLICITADO);
        assertThat(resp.acessoAte()).isEqualTo(s.getNextDueDate());
    }

    @Test
    void idempotente_jaCancelando_naoChamaAsaas() {
        SubscriptionService service = new SubscriptionService(subscriptionRepository, asaasGateway);
        Subscription s = sub(SubscriptionStatus.CANCELAMENTO_SOLICITADO, "sub_1");
        when(subscriptionRepository.findByTenantId(1L)).thenReturn(List.of(s));

        CancelSubscriptionResponse resp = service.cancelForTenant(1L);

        verify(asaasGateway, never()).cancelSubscription(anyString());
        assertThat(resp.status()).isEqualTo(SubscriptionStatus.CANCELAMENTO_SOLICITADO);
    }

    @Test
    void semAssinaturaCancelavel_404() {
        SubscriptionService service = new SubscriptionService(subscriptionRepository, asaasGateway);
        when(subscriptionRepository.findByTenantId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.cancelForTenant(1L))
                .isInstanceOf(ResponseStatusException.class);
        verify(asaasGateway, never()).cancelSubscription(anyString());
    }
}
