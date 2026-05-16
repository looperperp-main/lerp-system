package com.l.erp.partnerservice.infra.kafka;

import com.l.erp.partnerservice.domain.PartnerReferral;
import com.l.erp.partnerservice.repository.PartnerReferralRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class InviteTenantCreatedConsumer {

    private static final Logger logger = LoggerFactory.getLogger(InviteTenantCreatedConsumer.class);

    private final PartnerReferralRepository referralRepository;
    private final ObjectMapper objectMapper;

    public InviteTenantCreatedConsumer(PartnerReferralRepository referralRepository, ObjectMapper objectMapper) {
        this.referralRepository = referralRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "partner.invite.processed", groupId = "partner-service-group")
    public void consume(String payload) {
        logger.info("Recebido evento partner.invite.processed");
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});

            UUID referralId = UUID.fromString((String) data.get("partnerReferralId"));
            Long tenantId = ((Number) data.get("tenantId")).longValue();
            String activationToken = (String) data.get("activationToken");
            String tokenExpiresAtStr = (String) data.get("tokenExpiresAt");

            PartnerReferral referral = referralRepository.findById(referralId).orElse(null);
            if (referral == null) {
                logger.warn("PartnerReferral não encontrado para id={}, ignorando evento", referralId);
                return;
            }

            referral.setTenantId(tenantId);
            referral.setActivationToken(activationToken);
            referral.setTokenExpiresAt(OffsetDateTime.parse(tokenExpiresAtStr));
            referralRepository.save(referral);

            logger.info("PartnerReferral {} atualizado com tenantId={}", referralId, tenantId);
        } catch (Exception e) {
            logger.error("Falha ao processar partner.invite.processed. Payload: {}", payload, e);
        }
    }
}