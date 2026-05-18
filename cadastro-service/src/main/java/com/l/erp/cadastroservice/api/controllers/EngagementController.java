package com.l.erp.cadastroservice.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.cadastroservice.util.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/engagement")
public class EngagementController {

    private static final Logger logger = LoggerFactory.getLogger(EngagementController.class);
    private static final String TOPIC = "trial.feature.used";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public EngagementController(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public record FeatureRequest(@NotBlank @Size(max = 100) String featureKey) {}

    @PostMapping("/feature")
    public ResponseEntity<Void> registrarFeature(@RequestBody @Valid FeatureRequest req) {
        SecurityUtils.getCurrentTenantId().ifPresent(tenantId -> {
            try {
                String payload = objectMapper.writeValueAsString(Map.of(
                        "tenantId", tenantId,
                        "featureKey", req.featureKey()
                ));
                kafkaTemplate.send(TOPIC, String.valueOf(tenantId), payload);
            } catch (Exception e) {
                logger.error("Falha ao publicar trial.feature.used tenantId={} feature={}", tenantId, req.featureKey(), e);
            }
        });
        return ResponseEntity.accepted().build();
    }
}