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
public class LoginAuditConsumer {

    private static final Logger logger = LoggerFactory.getLogger(LoginAuditConsumer.class);
    private static final List<String> ACTIVE_TRIAL_STATUSES = List.of("TRIAL", "FOLLOWUP");

    private final PartnerReferralRepository referralRepository;
    private final TrialEngagementService engagementService;
    private final ObjectMapper objectMapper;

    public LoginAuditConsumer(PartnerReferralRepository referralRepository,
                              TrialEngagementService engagementService,
                              ObjectMapper objectMapper) {
        this.referralRepository = referralRepository;
        this.engagementService = engagementService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "trial.login", groupId = "partner-service-group")
    public void consume(String payload) {
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});
            Long tenantId = ((Number) data.get("tenantId")).longValue();

            boolean isActiveTrial = referralRepository
                    .findByTenantIdAndStatusIn(tenantId, ACTIVE_TRIAL_STATUSES)
                    .isPresent();

            if (isActiveTrial) {
                engagementService.registrar(tenantId, TrialEngagementService.FEATURE_LOGIN);
                logger.debug("Login registrado em TrialEngagement para tenantId={}", tenantId);
            }
        } catch (Exception e) {
            logger.error("Falha ao processar trial.login. Payload: {}", payload, e);
        }
    }
}