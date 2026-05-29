package com.l.erp.billingservice.infra.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.billingservice.api.dto.ComissaoItemDTO;
import com.l.erp.billingservice.api.dto.ExtratoComissoesDTO;
import com.l.erp.billingservice.services.CommissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
        var id = UUID.fromString(partnerId);

        var mesAtual = commissionService.getComissaoMesAtual(id);
        var totalPago = commissionService.getTotalPago(id);
        var historico = commissionService.findByPartner(id).stream()
                .map(c -> new ComissaoItemDTO(
                        c.getId(), c.getTenantId(), c.getAmount(),
                        c.getPeriod(), c.getStatus(), c.getCalculatedAt(), c.getPaidAt()))
                .toList();

        var extrato = new ExtratoComissoesDTO(
                mesAtual != null ? mesAtual : BigDecimal.ZERO,
                totalPago != null ? totalPago : BigDecimal.ZERO,
                historico);

        return objectMapper.writeValueAsString(extrato);
    }
}