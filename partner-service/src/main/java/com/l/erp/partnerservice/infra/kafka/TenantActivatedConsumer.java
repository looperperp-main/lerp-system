package com.l.erp.partnerservice.infra.kafka;

import com.l.erp.partnerservice.domain.PartnerReferral;
import com.l.erp.partnerservice.repository.PartnerReferralRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class TenantActivatedConsumer {

    private static final Logger logger = LoggerFactory.getLogger(TenantActivatedConsumer.class);

    private final PartnerReferralRepository referralRepository;
    private final ObjectMapper objectMapper;

    public TenantActivatedConsumer(PartnerReferralRepository referralRepository, ObjectMapper objectMapper) {
        this.referralRepository = referralRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "partner.tenant.activated", groupId = "partner-service-group")
    @Transactional
    public void consume(String payload) {
        logger.info("Recebido evento partner.tenant.activated");
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});

            String referralIdStr = (String) data.get("referralId");
            String trialStartedAtStr = (String) data.get("trialStartedAt");
            String trialExpiresAtStr = (String) data.get("trialExpiresAt");

            if (referralIdStr == null) {
                logger.warn("Evento partner.tenant.activated sem referralId, ignorando");
                return;
            }

            UUID referralId = UUID.fromString(referralIdStr);
            Optional<PartnerReferral> opt = referralRepository.findById(referralId);
            if (opt.isEmpty()) {
                logger.warn("PartnerReferral {} não encontrado, ignorando evento", referralId);
                return;
            }

            PartnerReferral referral = opt.get();
            referral.setActivationToken(null);
            referral.setActivatedAt(OffsetDateTime.now());
            referral.setStatus("TRIAL");

            if (trialStartedAtStr != null) {
                referral.setTrialStartedAt(OffsetDateTime.ofInstant(Instant.parse(trialStartedAtStr), ZoneOffset.UTC));
            }
            if (trialExpiresAtStr != null) {
                referral.setTrialExpiresAt(OffsetDateTime.ofInstant(Instant.parse(trialExpiresAtStr), ZoneOffset.UTC));
            }

            referralRepository.save(referral);
            logger.info("PartnerReferral {} atualizado: ATIVADO, trial expira em {}", referralId, trialExpiresAtStr);

        } catch (Exception e) {
            logger.error("Falha ao processar partner.tenant.activated. Payload: {}", payload, e);
        }
    }
}