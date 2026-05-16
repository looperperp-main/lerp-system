package com.l.erp.authservice.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.dominio.enumerators.EnumTenantStatus;
import com.l.erp.authservice.infra.TokenService;
import com.l.erp.authservice.repositorios.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class InviteRequestedConsumer {

    private static final Logger logger = LoggerFactory.getLogger(InviteRequestedConsumer.class);
    private static final String TOPIC_INVITE_PROCESSED = "partner.invite.processed";

    private final TenantRepository tenantRepository;
    private final TokenService tokenService;
    private final EmailConsumerService emailConsumerService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public InviteRequestedConsumer(TenantRepository tenantRepository,
                                   TokenService tokenService,
                                   EmailConsumerService emailConsumerService,
                                   KafkaTemplate<String, String> kafkaTemplate,
                                   ObjectMapper objectMapper) {
        this.tenantRepository = tenantRepository;
        this.tokenService = tokenService;
        this.emailConsumerService = emailConsumerService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "partner.invite.requested", groupId = "auth-service-group")
    public void consume(String payload) {
        logger.info("Recebido evento partner.invite.requested");
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});

            String partnerReferralId = (String) data.get("partnerReferralId");
            String partnerId = (String) data.get("partnerId");
            String partnerName = (String) data.get("partnerName");
            String cnpj = (String) data.get("cnpj");
            String razaoSocial = (String) data.get("razaoSocial");
            String nomeFantasia = (String) data.get("nomeFantasia");
            String emailContato = (String) data.get("emailContato");
            String telefone = (String) data.get("telefone");
            Object tenantIdObj = data.get("tenantId");

            Tenant tenant;

            if (tenantIdObj != null) {
                // Reenvio: tenant já existe
                Long tenantId = ((Number) tenantIdObj).longValue();
                Optional<Tenant> existing = tenantRepository.findById(tenantId);
                if (existing.isEmpty()) {
                    logger.warn("Tenant {} não encontrado para reenvio, ignorando", tenantId);
                    return;
                }
                tenant = existing.get();
                if (tenant.getStatus() != EnumTenantStatus.CONVIDADO) {
                    logger.warn("Tenant {} não está CONVIDADO (status={}), ignorando reenvio", tenantId, tenant.getStatus());
                    return;
                }
            } else {
                // Convite inicial: verificar se CNPJ já tem tenant (idempotência)
                Optional<Tenant> existing = tenantRepository.findByCnpj(cnpj);
                if (existing.isPresent()) {
                    Tenant found = existing.get();
                    if (found.getStatus() == EnumTenantStatus.CONVIDADO) {
                        tenant = found;
                        logger.info("Tenant CONVIDADO já existe para CNPJ {}, reutilizando id={}", cnpj, tenant.getId());
                    } else {
                        logger.warn("Tenant para CNPJ {} já existe com status={}, ignorando convite", cnpj, found.getStatus());
                        return;
                    }
                } else {
                    tenant = new Tenant();
                    tenant.setName(razaoSocial);
                    tenant.setCnpj(cnpj);
                    tenant.setNomeFantasia(nomeFantasia);
                    tenant.setEmail(emailContato);
                    tenant.setTelefone(telefone);
                    tenant.setStatus(EnumTenantStatus.CONVIDADO);
                    tenant.setCreationDate(Instant.now());
                    tenant.setCreatedBy("partner:" + partnerId);
                    tenant = tenantRepository.save(tenant);
                    logger.info("Tenant CONVIDADO criado id={} para CNPJ {}", tenant.getId(), cnpj);
                }
            }

            String clientEmail = tenant.getEmail() != null ? tenant.getEmail() : emailContato;
            String activationToken = tokenService.generateInvitationToken(
                    tenant.getId(), tenant.getCnpj(), tenant.getName(), partnerReferralId, clientEmail);
            OffsetDateTime tokenExpiresAt = OffsetDateTime.now().plus(7, ChronoUnit.DAYS);
            emailConsumerService.sendClientInviteEmail(
                    tenant.getName(), clientEmail, partnerName, activationToken, tokenExpiresAt);

            publishInviteProcessed(partnerReferralId, tenant.getId(), activationToken, tokenExpiresAt);

        } catch (Exception e) {
            logger.error("Falha ao processar partner.invite.requested. Payload: {}", payload, e);
        }
    }

    private void publishInviteProcessed(String partnerReferralId, Long tenantId,
                                        String activationToken, OffsetDateTime tokenExpiresAt) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("partnerReferralId", partnerReferralId);
            event.put("tenantId", tenantId);
            event.put("activationToken", activationToken);
            event.put("tokenExpiresAt", tokenExpiresAt.toString());

            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC_INVITE_PROCESSED, partnerReferralId, payload);
            logger.info("Evento partner.invite.processed publicado para referralId={}", partnerReferralId);
        } catch (Exception e) {
            logger.error("Falha ao publicar partner.invite.processed para referralId={}", partnerReferralId, e);
        }
    }
}