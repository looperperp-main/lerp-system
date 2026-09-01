package com.l.erp.partnerservice.infra.kafka;

import com.l.erp.partnerservice.services.TrialEngagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
public class FeatureAccessConsumer {

    private static final Logger logger = LoggerFactory.getLogger(FeatureAccessConsumer.class);

    private final TrialEngagementService engagementService;
    private final ObjectMapper objectMapper;

    public FeatureAccessConsumer(TrialEngagementService engagementService,
                                 ObjectMapper objectMapper) {
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

            // ponytail: sem filtro de status — engajamento agora é rastreado pra qualquer tenant
            // (não só TRIAL/FOLLOWUP), pra alimentar a Visão 360 do admin em qualquer status.
            engagementService.registrar(tenantId, featureKey);
            logger.debug("Feature '{}' registrada para tenantId={}", featureKey, tenantId);
        } catch (Exception e) {
            logger.error("Falha ao processar trial.feature.used. Payload: {}", payload, e);
        }
    }
}