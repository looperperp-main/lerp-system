package com.l.erp.partnerservice.services;

import com.l.erp.partnerservice.domain.PartnerReferral;
import com.l.erp.partnerservice.domain.TrialEngagement;
import com.l.erp.partnerservice.infra.kafka.KafkaPartnerProducerService;
import com.l.erp.partnerservice.infra.kafka.PartnerEmailNotificationEvent;
import com.l.erp.partnerservice.repository.PartnerReferralRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TrialScheduler {

    private static final Logger logger = LoggerFactory.getLogger(TrialScheduler.class);

    private final PartnerReferralRepository referralRepository;
    private final TrialEngagementService engagementService;
    private final KafkaPartnerProducerService kafkaProducer;

    public TrialScheduler(PartnerReferralRepository referralRepository,
                          TrialEngagementService engagementService,
                          KafkaPartnerProducerService kafkaProducer) {
        this.referralRepository = referralRepository;
        this.engagementService = engagementService;
        this.kafkaProducer = kafkaProducer;
    }

    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void processarD10() {
        OffsetDateTime now = OffsetDateTime.now();
        // Janela: trials que completaram 10 dias (entre 9 e 11 dias atrás — tolerância de 1 dia)
        List<PartnerReferral> referrals = referralRepository.findByStatusAndTrialStartedAtBetween(
                "TRIAL", now.minusDays(11), now.minusDays(9));

        logger.info("Scheduler D+10: {} referrals encontrados", referrals.size());

        for (PartnerReferral referral : referrals) {
            try {
                enviarRelatorioD10(referral);
            } catch (Exception e) {
                logger.error("Falha ao processar D+10 para referralId={}", referral.getId(), e);
            }
        }
    }

    @Scheduled(cron = "0 5 8 * * *")
    @Transactional
    public void processarD15() {
        OffsetDateTime now = OffsetDateTime.now();
        List<PartnerReferral> referrals = referralRepository
                .findByStatusAndTrialExpiresAtLessThanEqual("TRIAL", now);

        logger.info("Scheduler D+15: {} referrals com trial expirado", referrals.size());

        for (PartnerReferral referral : referrals) {
            try {
                transicionarParaFollowup(referral);
            } catch (Exception e) {
                logger.error("Falha ao processar D+15 para referralId={}", referral.getId(), e);
            }
        }
    }

    private void enviarRelatorioD10(PartnerReferral referral) {
        if (referral.getTenantId() == null) return;

        var features = engagementService.getEngagement(referral.getTenantId());
        var gaps = engagementService.getAdoptionGaps(referral.getTenantId());
        Optional<TrialEngagement> loginStats = engagementService.getLoginStats(referral.getTenantId());
        int loginCount = loginStats.map(TrialEngagement::getAccessCount).orElse(0);

        List<String> featureLabels = features.stream()
                .filter(f -> f.accessCount() > 0)
                .map(f -> f.label() + " (" + f.accessCount() + "×)")
                .toList();

        Map<String, Object> extra = new HashMap<>();
        extra.put("loginCount", loginCount);
        extra.put("features", featureLabels);
        extra.put("gaps", gaps);

        var partner = referral.getPartner();
        kafkaProducer.sendEmailNotification(new PartnerEmailNotificationEvent(
                PartnerEmailNotificationEvent.RELATORIO_D10,
                partner.getEmail(),
                partner.getName(),
                referral.getRazaoSocial(),
                referral.getCnpj(),
                extra
        ));

        logger.info("Relatório D+10 enviado para parceiro {} sobre cliente {}", partner.getEmail(), referral.getRazaoSocial());
    }

    private void transicionarParaFollowup(PartnerReferral referral) {
        referral.setStatus("FOLLOWUP");
        referral.setFollowupAttempts(0);
        referralRepository.save(referral);

        var partner = referral.getPartner();

        // Notifica o parceiro
        kafkaProducer.sendEmailNotification(new PartnerEmailNotificationEvent(
                PartnerEmailNotificationEvent.TRIAL_EXPIROU,
                partner.getEmail(),
                partner.getName(),
                referral.getRazaoSocial(),
                referral.getCnpj(),
                Map.of()
        ));

        // Notifica o cliente (se tiver email)
        if (referral.getEmailContato() != null) {
            kafkaProducer.sendEmailNotification(new PartnerEmailNotificationEvent(
                    PartnerEmailNotificationEvent.TRIAL_EXPIROU_CLIENTE,
                    referral.getEmailContato(),
                    partner.getName(),
                    referral.getRazaoSocial(),
                    referral.getCnpj(),
                    Map.of()
            ));
        }

        logger.info("Referral {} transicionado para FOLLOWUP", referral.getId());
    }
}