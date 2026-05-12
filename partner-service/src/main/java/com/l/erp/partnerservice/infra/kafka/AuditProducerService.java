package com.l.erp.partnerservice.infra.kafka;

import com.l.erp.common.api.dto.AuditEventDTO;
import com.l.erp.common.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuditProducerService {

    private final Logger logger = LoggerFactory.getLogger(AuditProducerService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public AuditProducerService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendAuditEvent(AuditEventDTO event) {
        String payload = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(Constants.AUDIT_TOPIC, String.valueOf(event.actorId()), payload);
        logger.debug("Evento de auditoria enviado para o Kafka com sucesso.");
    }
}