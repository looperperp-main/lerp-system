package com.l.erp.authservice.api.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


import com.l.erp.authservice.services.audit.AuditService;
import com.l.erp.common.api.dto.AuditEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component

public class AuditConsumer {

    private final Logger logger = LoggerFactory.getLogger(AuditConsumer.class);
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AuditConsumer(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;

        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "audit.events", groupId = "auth-service-group")
    public void consumeAuditEvent(String payload) {

        try {
            // Jackson faz o trabalho de transformar de volta pra Record/Objeto
            AuditEventDTO event = objectMapper.readValue(payload, AuditEventDTO.class);
            logger.info("Consumindo evento de auditoria: {} para ActorId: {}", event.action(), event.actorId());
            // Aqui você adapta para a assinatura do seu métod existente logAuditEventWithActor
            // Se a assinatura pedir tenant, você terá que ajustar o seu auditService também
            auditService.logAuditEventWithActorAndTimestamp(
                    event.action(),
                    event.actorId(),
                    event.targetType(),
                    event.targetId(),
                    event.result(),
                    event.detailsJson(),
                    event.correlationId(),
                    event.timestamp()
            );
        } catch (JsonProcessingException e) {
            logger.error("Erro fatal: Falha ao desserializar mensagem do Kafka para AuditEventDTO. Mensagem: {}", payload, e);
        }

    }

}
