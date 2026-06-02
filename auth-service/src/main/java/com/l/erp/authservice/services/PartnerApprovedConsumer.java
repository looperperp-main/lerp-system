package com.l.erp.authservice.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.repositorios.UserAccountRepository;
import com.l.erp.common.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class PartnerApprovedConsumer {

    private static final Logger logger = LoggerFactory.getLogger(PartnerApprovedConsumer.class);
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789@#$%";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectMapper objectMapper;
    private final EmailConsumerService emailConsumerService;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public PartnerApprovedConsumer(ObjectMapper objectMapper,
                                   EmailConsumerService emailConsumerService,
                                   UserAccountRepository userAccountRepository,
                                   PasswordEncoder passwordEncoder) {
        this.objectMapper = objectMapper;
        this.emailConsumerService = emailConsumerService;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @KafkaListener(topics = "partner.approved", groupId = "auth-service-group")
    public void consume(String payload) {
        logger.info("Recebido evento partner.approved");
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});
            String name = (String) data.get("name");
            String email = (String) data.get("email");
            String referralCode = (String) data.get("referralCode");
            UUID partnerId = UUID.fromString((String) data.get("partnerId"));

            if (userAccountRepository.findByEmail(email).isPresent()) {
                logger.warn("Parceiro já possui usuário cadastrado, ignorando evento: {}", email);
                return;
            }

            String tempPassword = generateTempPassword();

            UserAccount user = new UserAccount();
            user.setEmail(email);
            user.setDisplayName(name);
            user.setPasswordHash(passwordEncoder.encode(tempPassword));
            user.setActive(true);
            user.setFailedLoginAttempts(0);
            user.setCreatedDate(Instant.now());
            user.setCreatedBy(Constants.SYSTEM);
            user.setPartnerId(partnerId);
            user.setUserType(Constants.PARTNER);

            userAccountRepository.save(user);
            logger.info("Usuário parceiro criado com sucesso: {}", email);

            emailConsumerService.sendPartnerWelcomeEmail(name, email, referralCode, tempPassword);
        } catch (Exception e) {
            logger.error("Falha ao processar evento partner.approved. Payload: {}", payload, e);
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