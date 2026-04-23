package com.l.erp.authservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class EmailNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationService.class);
    public static final String EMAIL_TOPIC = "user-welcome-email-topic";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public EmailNotificationService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendWelcomeEmailEvent(String userEmail, String userName) {
        try {
            Map<String, String> payloadMap = new HashMap<>();
            payloadMap.put("email", userEmail);
            payloadMap.put("name", userName);
            payloadMap.put("type", "WELCOME");

            String payload = objectMapper.writeValueAsString(payloadMap);

            kafkaTemplate.send(EMAIL_TOPIC, userEmail, payload);
            logger.debug("Evento de e-mail enviado para o Kafka para: {}", userEmail);
        } catch (Exception e) {
            // Repassamos a exceção para que o UserService faça o catch e logue sem quebrar a criação
            throw new RuntimeException("Erro ao serializar ou enviar evento de email", e);
        }
    }
}
