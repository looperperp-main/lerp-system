package com.l.erp.partnerservice.infra.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaPartnerProducerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaPartnerProducerService.class);
    private static final String TOPIC_PARTNER_APPROVED = "partner.approved";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaPartnerProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendPartnerApproved(PartnerApprovedEvent event) {
        logger.info("Publicando evento PARTNER_APPROVED para parceiro id={}", event.partnerId());
        kafkaTemplate.send(TOPIC_PARTNER_APPROVED, String.valueOf(event.partnerId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        logger.error("Falha ao publicar PARTNER_APPROVED id={}", event.partnerId(), ex);
                    } else {
                        logger.info("PARTNER_APPROVED publicado - offset={}", result.getRecordMetadata().offset());
                    }
                });
    }
}