package com.l.erp.partnerservice.infra.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaPartnerProducerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaPartnerProducerService.class);
    private static final String TOPIC_PARTNER_APPROVED    = "partner.approved";
    private static final String TOPIC_INVITE_REQUESTED    = "partner.invite.requested";
    private static final String TOPIC_EMAIL_NOTIFICATION  = "partner.email.notification";
    private static final String TOPIC_COMMISSION_CALCULATED = "partner.commission.calculated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaPartnerProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendEmailNotification(PartnerEmailNotificationEvent event) {
        logger.info("Publicando partner.email.notification type={} to={}", event.type(), event.to());
        kafkaTemplate.send(TOPIC_EMAIL_NOTIFICATION, event.to(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        logger.error("Falha ao publicar partner.email.notification type={}", event.type(), ex);
                    }
                });
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

    public void sendCommissionCalculated(java.util.UUID partnerId, Long tenantId,
                                          String asaasSubscriptionId, String asaasPaymentId,
                                          java.math.BigDecimal amount) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("partnerId", partnerId.toString());
        payload.put("tenantId", tenantId);
        payload.put("asaasSubscriptionId", asaasSubscriptionId);
        payload.put("asaasPaymentId", asaasPaymentId != null ? asaasPaymentId : "");
        payload.put("amount", amount);
        logger.info("Publicando partner.commission.calculated partnerId={} amount={}", partnerId, amount);
        kafkaTemplate.send(TOPIC_COMMISSION_CALCULATED, partnerId.toString(), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        logger.error("Falha ao publicar partner.commission.calculated partnerId={}", partnerId, ex);
                    }
                });
    }

    public void sendInviteRequested(PartnerInviteRequestedEvent event) {
        logger.info("Publicando evento PARTNER_INVITE_REQUESTED referralId={}", event.partnerReferralId());
        kafkaTemplate.send(TOPIC_INVITE_REQUESTED, String.valueOf(event.partnerId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        logger.error("Falha ao publicar PARTNER_INVITE_REQUESTED referralId={}", event.partnerReferralId(), ex);
                    } else {
                        logger.info("PARTNER_INVITE_REQUESTED publicado - offset={}", result.getRecordMetadata().offset());
                    }
                });
    }
}