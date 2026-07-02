package com.l.erp.billingservice.infra.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.billingservice.services.CommissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ExtratoRequestConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExtratoRequestConsumer.class);

    private final CommissionService commissionService;
    private final ObjectMapper objectMapper;

    public ExtratoRequestConsumer(CommissionService commissionService, ObjectMapper objectMapper) {
        this.commissionService = commissionService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "partner.extrato.request", groupId = "billing-extrato-group")
    @SendTo("!")
    public String handleExtratoRequest(String partnerId) throws JsonProcessingException {
        log.info("Recebendo solicitação de extrato via Kafka para partnerId={}", partnerId);
        var extrato = commissionService.getExtrato(UUID.fromString(partnerId));
        return objectMapper.writeValueAsString(extrato);
    }
}