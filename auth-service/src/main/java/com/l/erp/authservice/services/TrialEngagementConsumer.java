package com.l.erp.authservice.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class TrialEngagementConsumer {

    private static final Logger log = LoggerFactory.getLogger(TrialEngagementConsumer.class);

    private final TrialEngagementService trialEngagementService;
    private final ObjectMapper objectMapper;

    public TrialEngagementConsumer(TrialEngagementService trialEngagementService, ObjectMapper objectMapper) {
        this.trialEngagementService = trialEngagementService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "trial.feature.used", groupId = "auth-service-group")
    public void consume(String payload) {
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});
            Long tenantId = ((Number) data.get("tenantId")).longValue();
            String featureKey = (String) data.get("featureKey");
            if (tenantId != null && featureKey != null && !featureKey.isBlank()) {
                trialEngagementService.track(tenantId, featureKey);
            }
        } catch (Exception e) {
            log.error("Falha ao processar trial.feature.access: {}", payload, e);
        }
    }
}