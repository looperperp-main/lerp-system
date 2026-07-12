package com.l.erp.partnerservice.services;

import com.l.erp.partnerservice.api.dto.FeatureStatDTO;
import com.l.erp.partnerservice.domain.Partner;
import com.l.erp.partnerservice.domain.PartnerReferral;
import com.l.erp.partnerservice.domain.TrialEngagement;
import com.l.erp.partnerservice.infra.kafka.KafkaPartnerProducerService;
import com.l.erp.partnerservice.infra.kafka.PartnerEmailNotificationEvent;
import com.l.erp.partnerservice.repository.PartnerReferralRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrialSchedulerTest {

    @Mock
    private PartnerReferralRepository referralRepository;

    @Mock
    private TrialEngagementService engagementService;

    @Mock
    private KafkaPartnerProducerService kafkaProducer;

    @InjectMocks
    private TrialScheduler trialScheduler;

    private PartnerReferral buildReferral(Long tenantId, String emailContato) {
        Partner partner = new Partner();
        partner.setName("Contador XYZ");
        partner.setEmail("contador@teste.com");

        PartnerReferral referral = new PartnerReferral();
        referral.setId(UUID.randomUUID());
        referral.setPartner(partner);
        referral.setTenantId(tenantId);
        referral.setCnpj("11222333000181");
        referral.setRazaoSocial("Cliente LTDA");
        referral.setEmailContato(emailContato);
        referral.setStatus("TRIAL");
        referral.setFollowupAttempts(0);
        referral.setInvitedAt(OffsetDateTime.now());
        return referral;
    }

    // ── processarD10 ─────────────────────────────────────────────────────

    @Test
    void shouldSendRelatorioD10ForReferralWithTenant() {
        PartnerReferral referral = buildReferral(42L, "cliente@teste.com");
        when(referralRepository.findByStatusAndTrialStartedAtBetween(any(), any(), any()))
                .thenReturn(List.of(referral));
        when(engagementService.getEngagement(42L)).thenReturn(List.of(
                new FeatureStatDTO("nfe", "Emissão de NF-e", 5, OffsetDateTime.now())));
        when(engagementService.getAdoptionGaps(42L)).thenReturn(List.of("Relatórios financeiros"));
        when(engagementService.getLoginStats(42L)).thenReturn(Optional.of(loginStats(3)));

        trialScheduler.processarD10();

        verify(kafkaProducer).sendEmailNotification(argThat(event ->
                event.type().equals(PartnerEmailNotificationEvent.RELATORIO_D10)
                        && event.to().equals("contador@teste.com")));
    }

    @Test
    void shouldSkipReferralWithoutTenantOnD10() {
        PartnerReferral referral = buildReferral(null, "cliente@teste.com");
        when(referralRepository.findByStatusAndTrialStartedAtBetween(any(), any(), any()))
                .thenReturn(List.of(referral));

        trialScheduler.processarD10();

        verify(engagementService, never()).getEngagement(any());
        verify(kafkaProducer, never()).sendEmailNotification(any());
    }

    @Test
    void shouldContinueProcessingD10WhenOneReferralFails() {
        PartnerReferral failing = buildReferral(1L, "a@teste.com");
        PartnerReferral ok = buildReferral(2L, "b@teste.com");
        when(referralRepository.findByStatusAndTrialStartedAtBetween(any(), any(), any()))
                .thenReturn(List.of(failing, ok));
        when(engagementService.getEngagement(1L)).thenThrow(new RuntimeException("falha engajamento"));
        when(engagementService.getEngagement(2L)).thenReturn(List.of());
        when(engagementService.getAdoptionGaps(2L)).thenReturn(List.of());
        when(engagementService.getLoginStats(2L)).thenReturn(Optional.empty());

        trialScheduler.processarD10();

        verify(kafkaProducer, times(1)).sendEmailNotification(any());
    }

    private TrialEngagement loginStats(int accessCount) {
        TrialEngagement e = new TrialEngagement();
        e.setAccessCount(accessCount);
        return e;
    }

    // ── processarD15 ─────────────────────────────────────────────────────

    @Test
    void shouldTransitionToFollowupAndNotifyBothPartnerAndClient() {
        PartnerReferral referral = buildReferral(42L, "cliente@teste.com");
        when(referralRepository.findByStatusAndTrialExpiresAtLessThanEqual(any(), any()))
                .thenReturn(List.of(referral));

        trialScheduler.processarD15();

        assertThat(referral.getStatus()).isEqualTo("FOLLOWUP");
        assertThat(referral.getFollowupAttempts()).isZero();
        verify(referralRepository).save(referral);
        verify(kafkaProducer, times(2)).sendEmailNotification(any());
    }

    @Test
    void shouldNotifyOnlyPartnerWhenClientEmailMissing() {
        PartnerReferral referral = buildReferral(42L, null);
        when(referralRepository.findByStatusAndTrialExpiresAtLessThanEqual(any(), any()))
                .thenReturn(List.of(referral));

        trialScheduler.processarD15();

        verify(kafkaProducer, times(1)).sendEmailNotification(argThat(event ->
                event.type().equals(PartnerEmailNotificationEvent.TRIAL_EXPIROU)));
    }

    @Test
    void shouldContinueProcessingD15WhenOneReferralFails() {
        PartnerReferral failing = buildReferral(1L, "a@teste.com");
        PartnerReferral ok = buildReferral(2L, "b@teste.com");
        when(referralRepository.findByStatusAndTrialExpiresAtLessThanEqual(any(), any()))
                .thenReturn(List.of(failing, ok));
        when(referralRepository.save(failing)).thenThrow(new RuntimeException("falha ao salvar"));

        trialScheduler.processarD15();

        assertThat(ok.getStatus()).isEqualTo("FOLLOWUP");
        verify(referralRepository).save(ok);
    }
}
