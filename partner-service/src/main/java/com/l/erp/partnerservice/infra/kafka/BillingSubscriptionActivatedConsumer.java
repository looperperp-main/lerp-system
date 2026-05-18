package com.l.erp.partnerservice.infra.kafka;

import com.l.erp.partnerservice.domain.Partner;
import com.l.erp.partnerservice.domain.PartnerReferral;
import com.l.erp.partnerservice.repository.PartnerReferralRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class BillingSubscriptionActivatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(BillingSubscriptionActivatedConsumer.class);
    // CONVERTIDO incluso para capturar renovações de clientes já convertidos
    private static final List<String> ALL_ACTIVE_STATUSES = List.of("TRIAL", "FOLLOWUP", "CONVIDADO", "ATIVADO", "CONVERTIDO");
    private static final List<String> CONVERTIBLE_STATUSES = List.of("TRIAL", "FOLLOWUP", "CONVIDADO", "ATIVADO");

    private final PartnerReferralRepository referralRepository;
    private final KafkaPartnerProducerService kafkaProducer;
    private final ObjectMapper objectMapper;

    public BillingSubscriptionActivatedConsumer(PartnerReferralRepository referralRepository,
                                                 KafkaPartnerProducerService kafkaProducer,
                                                 ObjectMapper objectMapper) {
        this.referralRepository = referralRepository;
        this.kafkaProducer = kafkaProducer;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "billing.subscription.activated", groupId = "partner-service-group")
    @Transactional
    public void consume(String payload) {
        log.info("Recebido billing.subscription.activated");
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});

            Long tenantId = data.get("tenantId") instanceof Number n ? n.longValue() : null;
            String planType = (String) data.get("planType");
            BigDecimal planValue = data.get("value") instanceof Number n
                    ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
            String asaasSubscriptionId = (String) data.get("asaasSubscriptionId");
            String asaasPaymentId = (String) data.get("asaasPaymentId");

            if (tenantId == null) {
                log.warn("billing.subscription.activated sem tenantId — ignorando");
                return;
            }

            Optional<PartnerReferral> opt = referralRepository.findByTenantIdAndStatusIn(tenantId, ALL_ACTIVE_STATUSES);
            if (opt.isEmpty()) {
                log.info("Nenhum PartnerReferral encontrado para tenantId={} — tenant sem parceiro, ignorando", tenantId);
                return;
            }

            PartnerReferral referral = opt.get();
            boolean primeiraConversao = CONVERTIBLE_STATUSES.contains(referral.getStatus());

            if (primeiraConversao) {
                referral.setStatus("CONVERTIDO");
                referral.setConvertedAt(OffsetDateTime.now());
                referralRepository.save(referral);
                log.info("PartnerReferral {} marcado como CONVERTIDO para tenantId={}", referral.getId(), tenantId);
                notifyPartner(referral, planType, planValue);
            } else {
                log.info("Renovação para tenantId={} referral={} — emitindo comissão sem alterar status", tenantId, referral.getId());
            }

            emitirComissao(referral, tenantId, asaasSubscriptionId, asaasPaymentId, planValue);

        } catch (Exception e) {
            log.error("Falha ao processar billing.subscription.activated. Payload: {}", payload, e);
        }
    }

    private void emitirComissao(PartnerReferral referral, Long tenantId,
                                 String asaasSubscriptionId, String asaasPaymentId, BigDecimal planValue) {
        try {
            Partner partner = referral.getPartner();
            BigDecimal amount = planValue
                    .multiply(partner.getCommissionRate())
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);

            kafkaProducer.sendCommissionCalculated(
                    partner.getId(), tenantId, asaasSubscriptionId, asaasPaymentId, amount);
        } catch (Exception e) {
            log.error("Falha ao emitir partner.commission.calculated para referral {}", referral.getId(), e);
        }
    }

    private void notifyPartner(PartnerReferral referral, String planType, BigDecimal planValue) {
        try {
            Partner partner = referral.getPartner();
            BigDecimal commissionPreview = planValue
                    .multiply(partner.getCommissionRate())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            kafkaProducer.sendEmailNotification(new PartnerEmailNotificationEvent(
                    PartnerEmailNotificationEvent.CLIENTE_CONVERTEU,
                    partner.getEmail(),
                    partner.getName(),
                    referral.getRazaoSocial(),
                    referral.getCnpj(),
                    Map.of(
                            "planType", planType != null ? planType : "",
                            "planValue", planValue,
                            "commissionPreview", commissionPreview
                    )
            ));
        } catch (Exception e) {
            log.error("Falha ao notificar parceiro sobre conversão do referral {}", referral.getId(), e);
        }
    }
}