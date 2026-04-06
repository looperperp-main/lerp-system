package com.l.erp.cadastroservice.services;

import com.l.erp.cadastroservice.util.Constants;
import com.l.erp.common.api.dto.AuditEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuditProducerService {

    private final Logger logger = LoggerFactory.getLogger(AuditProducerService.class);

    // Kafka agora só aceita Strings
    private final KafkaTemplate<String, String> kafkaTemplate;

    // O Jackson fará o trabalho duro
    private final ObjectMapper objectMapper;

    public AuditProducerService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendAuditEvent(AuditEventDTO event) {
        // Serializa o DTO para JSON string
        String payload = objectMapper.writeValueAsString(event);

        // Envia como texto puro para o Kafka
        kafkaTemplate.send(Constants.AUDIT_TOPIC, String.valueOf(event.actorId()), payload);

        logger.debug("Evento de auditoria enviado para o Kafka com sucesso.");
    }
}
