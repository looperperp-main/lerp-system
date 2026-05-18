package com.l.erp.billingservice.infra.kafka;

import com.l.erp.billingservice.services.CommissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
public class PartnerCommissionCalculatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PartnerCommissionCalculatedConsumer.class);

    private final CommissionService commissionService;
    private final ObjectMapper objectMapper;

    public PartnerCommissionCalculatedConsumer(CommissionService commissionService, ObjectMapper objectMapper) {
        this.commissionService = commissionService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "partner.commission.calculated", groupId = "billing-service-group")
    public void consume(String payload) {
        log.info("Recebido partner.commission.calculated");
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});

            UUID partnerId = UUID.fromString((String) data.get("partnerId"));
            Long tenantId = data.get("tenantId") instanceof Number n ? n.longValue() : null;
            String asaasSubscriptionId = (String) data.get("asaasSubscriptionId");
            String asaasPaymentId = (String) data.get("asaasPaymentId");
            BigDecimal amount = data.get("amount") instanceof Number n
                    ? new BigDecimal(n.toString()) : BigDecimal.ZERO;

            if (tenantId == null || asaasSubscriptionId == null) {
                log.warn("partner.commission.calculated com dados incompletos — ignorando. payload={}", payload);
                return;
            }

            commissionService.createCommission(partnerId, tenantId, asaasSubscriptionId, amount, asaasPaymentId);

        } catch (Exception e) {
            log.error("Falha ao processar partner.commission.calculated. payload={}", payload, e);
        }
    }
}