package com.l.erp.authservice.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;

@Service
public class PartnerApprovedConsumer {

    private static final Logger logger = LoggerFactory.getLogger(PartnerApprovedConsumer.class);
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789@#$%";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectMapper objectMapper;
    private final EmailConsumerService emailConsumerService;

    public PartnerApprovedConsumer(ObjectMapper objectMapper, EmailConsumerService emailConsumerService) {
        this.objectMapper = objectMapper;
        this.emailConsumerService = emailConsumerService;
    }

    @KafkaListener(topics = "billing.partner.approved", groupId = "auth-service-group")
    public void consume(String payload) {
        logger.info("Recebido evento billing.partner.approved");
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});
            String name = (String) data.get("name");
            String email = (String) data.get("email");
            String referralCode = (String) data.get("referralCode");

            String tempPassword = generateTempPassword();
            emailConsumerService.sendPartnerWelcomeEmail(name, email, referralCode, tempPassword);
        } catch (Exception e) {
            logger.error("Falha ao processar evento billing.partner.approved. Payload: {}", payload, e);
        }
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}