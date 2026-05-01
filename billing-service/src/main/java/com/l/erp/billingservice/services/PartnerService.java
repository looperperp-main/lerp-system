package com.l.erp.billingservice.services;

import com.l.erp.billingservice.api.dto.PartnerRequestDTO;
import com.l.erp.billingservice.api.dto.PartnerReviewDTO;
import com.l.erp.billingservice.domain.Partner;
import com.l.erp.billingservice.infra.kafka.AuditProducerService;
import com.l.erp.billingservice.infra.kafka.KafkaPartnerProducerService;
import com.l.erp.billingservice.infra.kafka.PartnerApprovedEvent;
import com.l.erp.billingservice.repository.PartnerRepository;
import com.l.erp.billingservice.util.SecurityUtils;
import com.l.erp.common.api.dto.AuditEventDTO;
import com.l.erp.common.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.l.erp.billingservice.util.SecurityUtils.getCorrelationIdFromRequest;

@Service
public class PartnerService {

    private static final Logger logger = LoggerFactory.getLogger(PartnerService.class);


    private final PartnerRepository repository;
    private final KafkaPartnerProducerService kafkaProducer;
    private final AuditProducerService auditProducer;

    public PartnerService(PartnerRepository repository, KafkaPartnerProducerService kafkaProducer, AuditProducerService auditProducer) {
        this.repository = repository;
        this.kafkaProducer = kafkaProducer;
        this.auditProducer = auditProducer;
    }

    @Transactional(readOnly = true)
    public Page<Partner> findAll(String status, Pageable pageable) {
        logger.info("Listando parceiros (status={})", status);
        if (status != null && !status.isBlank()) {
            return repository.findByStatus(status, pageable);
        }
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Partner findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Parceiro não encontrado - id: " + id));
    }

    @Transactional
    public Partner save(PartnerRequestDTO dto, String createdBy) {
        logger.info("Criando parceiro: {}", dto.email());

        if (repository.existsByCnpj(dto.cnpj())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, Constants.PARCEIRO_CNPJ_ALREADY_EXISTS + ": " + dto.cnpj());
        }
        if (repository.existsByEmail(dto.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, Constants.PARCEIRO_EMAIL_ALREADY_EXISTS + ": " + dto.email());
        }

        Partner partner = new Partner();
        partner.setName(dto.name());
        partner.setCrc(dto.crc());
        partner.setCnpj(dto.cnpj());
        partner.setEmail(dto.email());
        partner.setPhone(dto.phone());
        partner.setReferralCode(generateReferralCode());
        partner.setCommissionRate(Constants.DEFAULT_COMMISSION_RATE);
        partner.setStatus(Constants.STATUS_PENDENTE);
        partner.setCreatedAt(OffsetDateTime.now());
        partner.setCreatedBy(createdBy);

        return repository.save(partner);
    }

    @Transactional
    public Partner update(UUID id, PartnerRequestDTO dto, String updatedBy) {
        logger.info("Atualizando parceiro {}", id);
        UUID correlationID = getCorrelationIdFromRequest(logger);
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND));

        Partner partner = repository.findById(id)
                .orElseThrow(() -> {
                    sendAuditEvent(Constants.PARCEIRO_UPDATE, userId, null, Constants.ERROR, "{ERROR: Parceiro não encontrado}", correlationID);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                        Constants.PARCEIRO_NOT_FOUND + Constants._ID + id);
                });

        if (!partner.getCnpj().equals(dto.cnpj()) && repository.existsByCnpj(dto.cnpj())) {
            sendAuditEvent(Constants.PARCEIRO_UPDATE, userId, null, Constants.ERROR, "{ERROR: CNPJ já existe}", correlationID);
            throw new ResponseStatusException(HttpStatus.CONFLICT, Constants.PARCEIRO_CNPJ_ALREADY_EXISTS + ": " + dto.cnpj());
        }
        if (!partner.getEmail().equals(dto.email()) && repository.existsByEmail(dto.email())) {
            sendAuditEvent(Constants.PARCEIRO_UPDATE, userId, null, Constants.ERROR, "{ERROR: Email já existe}", correlationID);
            throw new ResponseStatusException(HttpStatus.CONFLICT, Constants.PARCEIRO_EMAIL_ALREADY_EXISTS + ": " + dto.email());
        }

        partner.setName(dto.name());
        partner.setCrc(dto.crc());
        partner.setCnpj(dto.cnpj());
        partner.setEmail(dto.email());
        partner.setPhone(dto.phone());
        partner.setUpdatedAt(OffsetDateTime.now());
        partner.setUpdatedBy(updatedBy);

        Partner saved = repository.save(partner);
        sendAuditEvent(Constants.PARCEIRO_UPDATE, userId, saved.getId(), Constants.SUCCESS, null, correlationID);
        return saved;
    }

    @Transactional
    public Partner approve(UUID id, PartnerReviewDTO dto, String reviewedBy) {
        logger.info("Aprovando parceiro {}", id);
        UUID correlationID = getCorrelationIdFromRequest(logger);
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND));
        Partner partner  = repository.findById(id)
                .orElseThrow(() -> {
                    sendAuditEvent(Constants.PARCEIRO_UPDATE, userId, null, Constants.ERROR, "{ERROR: Parceiro não encontrado}", correlationID);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            Constants.PARCEIRO_NOT_FOUND + Constants._ID + id);}
                );

        if (!Constants.STATUS_PENDENTE.equals(partner.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Apenas parceiros com status PENDENTE podem ser aprovados. Status atual: " + partner.getStatus());
        }

        OffsetDateTime now = OffsetDateTime.now();
        partner.setStatus(Constants.STATUS_ATIVO);
        partner.setReviewedBy(reviewedBy);
        partner.setReviewedAt(now);
        partner.setReviewNotes(dto != null ? dto.notes() : null);
        partner.setUpdatedAt(now);
        partner.setUpdatedBy(reviewedBy);

        Partner saved = repository.save(partner);

        kafkaProducer.sendPartnerApproved(new PartnerApprovedEvent(
                saved.getId(),
                saved.getName(),
                saved.getEmail(),
                saved.getCnpj(),
                saved.getCrc(),
                saved.getPhone(),
                saved.getReferralCode(),
                saved.getCommissionRate(),
                reviewedBy,
                now
        ));

        return saved;
    }

    @Transactional
    public Partner inactivate(UUID id, String inactivatedBy) {
        logger.info("Inativando parceiro {}", id);
        UUID correlationID = getCorrelationIdFromRequest(logger);
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND));

        Partner partner = repository.findById(id)
                .orElseThrow(() -> {
                    sendAuditEvent(Constants.PARCEIRO_INACTIVATE, userId, null, Constants.ERROR, "{ERROR: Parceiro não encontrado}", correlationID);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            Constants.PARCEIRO_NOT_FOUND + Constants._ID + id);
                });

        if (!Constants.STATUS_ATIVO.equals(partner.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Apenas parceiros com status ATIVO podem ser inativados. Status atual: " + partner.getStatus());
        }

        OffsetDateTime now = OffsetDateTime.now();
        partner.setStatus(Constants.STATUS_INATIVO);
        partner.setUpdatedAt(now);
        partner.setUpdatedBy(inactivatedBy);

        Partner saved = repository.save(partner);
        sendAuditEvent(Constants.PARCEIRO_INACTIVATE, userId, saved.getId(), Constants.SUCCESS, null, correlationID);
        return saved;
    }

    @Transactional
    public Partner reject(UUID id, PartnerReviewDTO dto, String reviewedBy) {
        logger.info("Reprovando parceiro {}", id);

        Partner partner = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        Constants.PARCEIRO_NOT_FOUND + Constants._ID + id));

        if (!Constants.STATUS_PENDENTE.equals(partner.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Apenas parceiros com status PENDENTE podem ser reprovados. Status atual: " + partner.getStatus());
        }

        OffsetDateTime now = OffsetDateTime.now();
        partner.setStatus(Constants.STATUS_REPROVADO);
        partner.setReviewedBy(reviewedBy);
        partner.setReviewedAt(now);
        partner.setReviewNotes(dto != null ? dto.notes() : null);
        partner.setUpdatedAt(now);
        partner.setUpdatedBy(reviewedBy);

        return repository.save(partner);
    }

    private String generateReferralCode() {
        String code;
        do {
            int n = ThreadLocalRandom.current().nextInt(0, 100_000);
            code = String.format("CTR-%05d", n);
        } while (repository.existsByReferralCode(code));
        return code;
    }

    private void sendAuditEvent(String action, UUID actorId, UUID targetId, String result, String detailsJson, UUID correlationId) {
        AuditEventDTO auditEvent = new AuditEventDTO(
                action, actorId, Constants.PARCEIRO, targetId, result, detailsJson, correlationId, Instant.now()
        );
        auditProducer.sendAuditEvent(auditEvent);
    }
}