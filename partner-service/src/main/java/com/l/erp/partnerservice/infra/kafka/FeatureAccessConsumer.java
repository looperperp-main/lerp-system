package com.l.erp.partnerservice.infra.kafka;

import com.l.erp.partnerservice.repository.PartnerReferralRepository;
import com.l.erp.partnerservice.services.TrialEngagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Service
public class FeatureAccessConsumer {

    private static final Logger logger = LoggerFactory.getLogger(FeatureAccessConsumer.class);
    private static final List<String> ACTIVE_TRIAL_STATUSES = List.of("TRIAL", "FOLLOWUP");

    private final PartnerReferralRepository referralRepository;
    private final TrialEngagementService engagementService;
    private final ObjectMapper objectMapper;

    public FeatureAccessConsumer(PartnerReferralRepository referralRepository,
                                 TrialEngagementService engagementService,
                                 ObjectMapper objectMapper) {
        this.referralRepository = referralRepository;
        this.engagementService = engagementService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "trial.feature.used", groupId = "partner-service-group")
    public void consume(String payload) {
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});
            Long tenantId = ((Number) data.get("tenantId")).longValue();
            String featureKey = (String) data.get("featureKey");

            if (!TrialEngagementService.FEATURE_CATALOG.containsKey(featureKey)) {
                return;
            }

            boolean isActiveTrial = referralRepository
                    .findByTenantIdAndStatusIn(tenantId, ACTIVE_TRIAL_STATUSES)
                    .isPresent();

            if (isActiveTrial) {
                engagementService.registrar(tenantId, featureKey);
                logger.debug("Feature '{}' registrada para tenantId={}", featureKey, tenantId);
            }
        } catch (Exception e) {
            logger.error("Falha ao processar trial.feature.used. Payload: {}", payload, e);
        }
    }
}