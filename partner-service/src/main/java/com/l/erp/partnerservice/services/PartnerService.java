package com.l.erp.partnerservice.services;

import com.l.erp.common.api.dto.AuditEventDTO;
import com.l.erp.common.util.Constants;
import com.l.erp.partnerservice.api.dto.AtividadeItemDTO;
import com.l.erp.partnerservice.api.dto.ClienteDetalheResponseDTO;
import com.l.erp.partnerservice.api.dto.ComissaoItemDTO;
import com.l.erp.partnerservice.api.dto.ConviteRequestDTO;
import com.l.erp.partnerservice.api.dto.ConviteResponseDTO;
import com.l.erp.partnerservice.api.dto.DashboardResponseDTO;
import com.l.erp.partnerservice.api.dto.ExtratoComissoesDTO;
import com.l.erp.partnerservice.api.dto.FeatureStatDTO;
import com.l.erp.partnerservice.api.dto.FollowupRequestDTO;
import com.l.erp.partnerservice.api.dto.PartnerRequestDTO;
import com.l.erp.partnerservice.api.dto.PartnerReviewDTO;
import com.l.erp.partnerservice.api.dto.TrialUrgenteDTO;
import com.l.erp.partnerservice.domain.Partner;
import com.l.erp.partnerservice.domain.PartnerReferral;
import com.l.erp.partnerservice.infra.billing.BillingClient;
import com.l.erp.partnerservice.infra.billing.BillingComissaoItemDTO;
import com.l.erp.partnerservice.infra.billing.BillingExtratoDTO;
import com.l.erp.partnerservice.infra.kafka.AuditProducerService;
import com.l.erp.partnerservice.infra.kafka.KafkaPartnerProducerService;
import com.l.erp.partnerservice.infra.kafka.PartnerApprovedEvent;
import com.l.erp.partnerservice.infra.kafka.PartnerEmailNotificationEvent;
import com.l.erp.partnerservice.infra.kafka.PartnerInviteRequestedEvent;
import com.l.erp.partnerservice.repository.PartnerReferralRepository;
import com.l.erp.partnerservice.repository.PartnerRepository;
import com.l.erp.partnerservice.util.SecurityUtils;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.l.erp.partnerservice.util.SecurityUtils.getCorrelationIdFromRequest;

@Service
public class PartnerService {

    private static final Logger logger = LoggerFactory.getLogger(PartnerService.class);

    private final PartnerRepository repository;
    private final PartnerReferralRepository referralRepository;
    private final KafkaPartnerProducerService kafkaProducer;
    private final AuditProducerService auditProducer;
    private final TrialEngagementService engagementService;
    private final BillingClient billingClient;

    public PartnerService(PartnerRepository repository,
                          PartnerReferralRepository referralRepository,
                          KafkaPartnerProducerService kafkaProducer,
                          AuditProducerService auditProducer,
                          TrialEngagementService engagementService,
                          BillingClient billingClient) {
        this.repository = repository;
        this.referralRepository = referralRepository;
        this.kafkaProducer = kafkaProducer;
        this.auditProducer = auditProducer;
        this.engagementService = engagementService;
        this.billingClient = billingClient;
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

    @Transactional(readOnly = true)
    public Optional<Partner> findByEmail(String email) {
        return repository.findByEmail(email);
    }

    @Transactional
    public Partner save(PartnerRequestDTO dto, String createdBy) {
        logger.info("Criando parceiro: {}", dto.email());
        UUID correlationID = getCorrelationIdFromRequest(logger);
        UUID userId = SecurityUtils.getCurrentUserId().orElse(null);

        if (repository.existsByCnpj(dto.cnpj())) {
            sendAuditEvent(Constants.PARCEIRO_CREATION, userId, null, Constants.ERROR, "{\"error\": \"CNPJ já cadastrado\"}", correlationID);
            throw new ResponseStatusException(HttpStatus.CONFLICT, Constants.PARCEIRO_CNPJ_ALREADY_EXISTS + ": " + dto.cnpj());
        }
        if (repository.existsByEmail(dto.email())) {
            sendAuditEvent(Constants.PARCEIRO_CREATION, userId, null, Constants.ERROR, "{\"error\": \"E-mail já cadastrado\"}", correlationID);
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

        Partner saved = repository.save(partner);
        sendAuditEvent(Constants.PARCEIRO_CREATION, userId, saved.getId(), Constants.SUCCESS, null, correlationID);
        return saved;
    }

    @Transactional
    public Partner update(UUID id, PartnerRequestDTO dto, String updatedBy) {
        logger.info("Atualizando parceiro {}", id);
        UUID correlationID = getCorrelationIdFromRequest(logger);
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND));

        Partner partner = repository.findById(id)
                .orElseThrow(() -> {
                    sendAuditEvent(Constants.PARCEIRO_UPDATE, userId, null, Constants.ERROR, Constants.PARCEIRO_NOT_FOUND_EM, correlationID);
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

        Partner partner = repository.findById(id)
                .orElseThrow(() -> {
                    sendAuditEvent(Constants.PARCEIRO_UPDATE, userId, null, Constants.ERROR, Constants.PARCEIRO_NOT_FOUND_EM, correlationID);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            Constants.PARCEIRO_NOT_FOUND + Constants._ID + id);
                });

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
        sendAuditEvent(Constants.PARCEIRO_APPROVE, userId, saved.getId(), Constants.SUCCESS, null, correlationID);

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
        UUID correlationID = getCorrelationIdFromRequest(logger);
        UUID userId = SecurityUtils.getCurrentUserId().orElse(null);

        Partner partner = repository.findById(id)
                .orElseThrow(() -> {
                    sendAuditEvent(Constants.PARCEIRO_REJECT, userId, null, Constants.ERROR, "{\"error\": \"Parceiro não encontrado\"}", correlationID);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            Constants.PARCEIRO_NOT_FOUND + Constants._ID + id);
                });

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

        Partner saved = repository.save(partner);
        sendAuditEvent(Constants.PARCEIRO_REJECT, userId, saved.getId(), Constants.SUCCESS, null, correlationID);
        return saved;
    }

    @Transactional(readOnly = true)
    public DashboardResponseDTO getDashboard(UUID partnerId) {
        OffsetDateTime now = OffsetDateTime.now();

        long statsConvidados = referralRepository.countByPartner_IdAndStatus(partnerId, Constants.CONVIDADO);
        long statsTrial      = referralRepository.countByPartner_IdAndStatus(partnerId, Constants.TRIAL);
        long statsFollowup   = referralRepository.countByPartner_IdAndStatus(partnerId, Constants.FOLLOWUP);
        long statsAtivos     = referralRepository.countByPartner_IdAndStatusIn(partnerId, List.of(Constants.ATIVADO, Constants.CONVERTIDO));
        long statsConvertidos = referralRepository.countByPartner_IdAndStatus(partnerId, Constants.CONVERTIDO);
        long trialsExpirando3Dias = referralRepository.countByPartner_IdAndStatusAndTrialExpiresAtBefore(
                partnerId, Constants.TRIAL, now.plusDays(3));

        List<TrialUrgenteDTO> trialsUrgentes = referralRepository
                .findTop10ByPartner_IdAndStatusOrderByTrialExpiresAtAsc(partnerId, Constants.TRIAL)
                .stream()
                .map(r -> new TrialUrgenteDTO(
                        r.getId(),
                        r.getRazaoSocial(),
                        r.getCnpj(),
                        r.getTrialExpiresAt(),
                        r.getTrialExpiresAt() != null
                                ? ChronoUnit.DAYS.between(now, r.getTrialExpiresAt())
                                : 0))
                .toList();

        List<AtividadeItemDTO> recentes = buildAtividadeRecente(partnerId);

        BigDecimal comissaoMes = BigDecimal.ZERO;
        BigDecimal totalPago = BigDecimal.ZERO;
        try {
            BillingExtratoDTO extrato = billingClient.getExtrato(partnerId);
            if (extrato != null) {
                comissaoMes = extrato.comissaoMesAtual() != null ? extrato.comissaoMesAtual() : BigDecimal.ZERO;
                totalPago = extrato.totalPago() != null ? extrato.totalPago() : BigDecimal.ZERO;
            }
        } catch (Exception e) {
            logger.warn("Não foi possível buscar comissões do billing-service para parceiro {}: {}", partnerId, e.getMessage());
        }

        return new DashboardResponseDTO(
                statsConvidados, statsAtivos, statsTrial,
                statsFollowup, statsConvertidos, trialsExpirando3Dias,
                comissaoMes, totalPago,
                trialsUrgentes, recentes);
    }

    @Transactional(readOnly = true)
    public ExtratoComissoesDTO getComissoes(UUID partnerId) {
        BillingExtratoDTO raw = billingClient.getExtrato(partnerId);
        if (raw == null) {
            return new ExtratoComissoesDTO(BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }

        // Enriquece os itens com razaoSocial/cnpj do PartnerReferral local
        Map<Long, PartnerReferral> referralByTenant = referralRepository
                .findByPartner_Id(partnerId, org.springframework.data.domain.Pageable.unpaged())
                .stream()
                .filter(r -> r.getTenantId() != null)
                .collect(Collectors.toMap(PartnerReferral::getTenantId, r -> r, (a, _) -> a));

        List<ComissaoItemDTO> enriquecidos = raw.historico().stream()
                .map((BillingComissaoItemDTO item) -> {
                    PartnerReferral r = item.tenantId() != null ? referralByTenant.get(item.tenantId()) : null;
                    return new ComissaoItemDTO(
                            item.id(),
                            item.tenantId(),
                            r != null ? r.getRazaoSocial() : "—",
                            r != null ? r.getCnpj() : "—",
                            item.amount(),
                            item.period(),
                            item.status(),
                            item.calculatedAt(),
                            item.paidAt()
                    );
                })
                .toList();

        return new ExtratoComissoesDTO(raw.comissaoMesAtual(), raw.totalPago(), enriquecidos);
    }

    private List<AtividadeItemDTO> buildAtividadeRecente(UUID partnerId) {
        List<AtividadeItemDTO> items = new ArrayList<>();

        referralRepository.findTop10ByPartner_IdOrderByInvitedAtDesc(partnerId)
                .forEach(r -> items.add(new AtividadeItemDTO(Constants.CONVIDADO, r.getRazaoSocial(), r.getInvitedAt())));

        referralRepository.findTop10ByPartner_IdAndActivatedAtNotNullOrderByActivatedAtDesc(partnerId)
                .forEach(r -> items.add(new AtividadeItemDTO(Constants.ATIVADO, r.getRazaoSocial(), r.getActivatedAt())));

        return items.stream()
                .sorted(Comparator.comparing(AtividadeItemDTO::timestamp).reversed())
                .limit(10)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClienteDetalheResponseDTO getClienteDetalhe(UUID referralId, UUID partnerId) {
        PartnerReferral referral = referralRepository.findByIdAndPartner_Id(referralId, partnerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, Constants.CONVITE_NOT_FOUND));

        ConviteResponseDTO conviteDTO = new ConviteResponseDTO(
                referral.getId(), referral.getCnpj(), referral.getRazaoSocial(),
                referral.getEmailContato(), referral.getStatus(),
                referral.getFollowupAttempts(), referral.getInvitedAt(), referral.getTokenExpiresAt(),
                referral.getPlanoSugerido(), referral.getTrialStartedAt(), referral.getTrialExpiresAt());

        int loginCount = 0;
        OffsetDateTime lastLoginAt = null;
        int daysActive = 0;

        if (referral.getTenantId() != null) {
            var loginStats = engagementService.getLoginStats(referral.getTenantId());
            if (loginStats.isPresent()) {
                loginCount = loginStats.get().getAccessCount();
                lastLoginAt = loginStats.get().getLastAccessedAt();
            }
            if (referral.getTrialStartedAt() != null) {
                daysActive = (int) java.time.temporal.ChronoUnit.DAYS.between(
                        referral.getTrialStartedAt(), OffsetDateTime.now());
            }
        }

        var features = referral.getTenantId() != null
                ? engagementService.getEngagement(referral.getTenantId())
                : List.of();
        var gaps = referral.getTenantId() != null
                ? engagementService.getAdoptionGaps(referral.getTenantId())
                : List.of();

        return new ClienteDetalheResponseDTO(conviteDTO, loginCount, lastLoginAt, daysActive, (List<FeatureStatDTO>) features, (List<String>) gaps);
    }

    @Transactional
    public void iniciarFollowup(UUID referralId, UUID partnerId, FollowupRequestDTO dto) {
        PartnerReferral referral = referralRepository.findByIdAndPartner_Id(referralId, partnerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, Constants.CONVITE_NOT_FOUND));

        if (!"FOLLOWUP".equals(referral.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Follow-up só pode ser iniciado em clientes com status FOLLOWUP. Status atual: " + referral.getStatus());
        }

        var partner = referral.getPartner();

        kafkaProducer.sendEmailNotification(new PartnerEmailNotificationEvent(
                PartnerEmailNotificationEvent.FOLLOWUP_MENSAGEM,
                referral.getEmailContato(),
                partner.getName(),
                referral.getRazaoSocial(),
                referral.getCnpj(),
                Map.of("message", dto.message())
        ));

        int tentativas = referral.getFollowupAttempts() + 1;
        referral.setFollowupAttempts(tentativas);

        if (tentativas >= 3) {
            referral.setStatus(Constants.PERDIDO);
            referralRepository.save(referral);
            kafkaProducer.sendEmailNotification(new PartnerEmailNotificationEvent(
                    PartnerEmailNotificationEvent.PERDIDO,
                    partner.getEmail(),
                    partner.getName(),
                    referral.getRazaoSocial(),
                    referral.getCnpj(),
                    Map.of()
            ));
            logger.info("Referral {} marcado como PERDIDO após {} tentativas", referralId, tentativas);
        } else {
            referralRepository.save(referral);
            logger.info("Follow-up {} enviado para referral {} (tentativa {}/3)", referralId, referralId, tentativas);
        }
    }

    @Transactional
    public PartnerReferral enviarConvite(UUID partnerId, ConviteRequestDTO dto) {
        logger.info("Enviando convite para CNPJ {} pelo parceiro {}", dto.cnpj(), partnerId);
        UUID correlationID = getCorrelationIdFromRequest(logger);

        Partner partner = repository.findById(partnerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, Constants.PARCEIRO_NOT_FOUND));

        if (!Constants.STATUS_ATIVO.equals(partner.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Apenas parceiros ativos podem enviar convites. Status atual: " + partner.getStatus());
        }

        if (referralRepository.existsByPartner_IdAndCnpjAndStatusIn(
                partnerId, dto.cnpj(), List.of(Constants.CONVIDADO, Constants.ATIVADO))) {
            sendAuditEvent(Constants.CONVITE_SEND, partnerId, null, Constants.ERROR,
                    "{\"error\": \"Convite ativo já existe\", \"cnpj\": \"" + dto.cnpj() + "\"}", correlationID);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe um convite ativo para o CNPJ: " + dto.cnpj());
        }

        OffsetDateTime now = OffsetDateTime.now();
        PartnerReferral referral = new PartnerReferral();
        referral.setPartner(partner);
        referral.setCnpj(dto.cnpj());
        referral.setRazaoSocial(dto.razaoSocial());
        referral.setEmailContato(dto.emailContato());
        referral.setPlanoSugerido(dto.planoSugerido());
        referral.setStatus(Constants.CONVIDADO);
        referral.setInvitedAt(now);
        referral.setFollowupAttempts(0);
        PartnerReferral saved = referralRepository.save(referral);
        sendAuditEvent(Constants.CONVITE_SEND, partnerId, saved.getId(), Constants.SUCCESS,
                "{\"cnpj\": \"" + dto.cnpj() + "\", \"plano\": \"" + dto.planoSugerido() + "\"}", correlationID);

        kafkaProducer.sendInviteRequested(new PartnerInviteRequestedEvent(
                saved.getId(),
                partnerId,
                partner.getName(),
                partner.getReferralCode(),
                null,
                dto.cnpj(),
                dto.razaoSocial(),
                dto.nomeFantasia(),
                dto.emailContato(),
                dto.telefone(),
                dto.planoSugerido(),
                now
        ));

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<ConviteResponseDTO> listarConvites(UUID partnerId, Pageable pageable) {
        return referralRepository.findByPartner_Id(partnerId, pageable)
                .map(r -> new ConviteResponseDTO(
                        r.getId(),
                        r.getCnpj(),
                        r.getRazaoSocial(),
                        r.getEmailContato(),
                        r.getStatus(),
                        r.getFollowupAttempts(),
                        r.getInvitedAt(),
                        r.getTokenExpiresAt(),
                        r.getPlanoSugerido(),
                        r.getTrialStartedAt(),
                        r.getTrialExpiresAt()
                ));
    }

    @Transactional
    public PartnerReferral reenviarConvite(UUID referralId, UUID partnerId) {
        logger.info("Reenviando convite referralId={} pelo parceiro {}", referralId, partnerId);
        UUID correlationID = getCorrelationIdFromRequest(logger);

        PartnerReferral referral = referralRepository.findByIdAndPartner_Id(referralId, partnerId)
                .orElseThrow(() -> {
                    sendAuditEvent(Constants.CONVITE_RESEND, partnerId, referralId, Constants.ERROR, "{\"error\": \"Convite não encontrado\"}", correlationID);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, Constants.CONVITE_NOT_FOUND);
                });

        if (!Constants.CONVIDADO.equals(referral.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Apenas convites "+Constants.CONVIDADO+" podem ser reenviados. Status atual: " + referral.getStatus());
        }

        if (referral.getTenantId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Convite ainda sendo processado, aguarde antes de reenviar");
        }

        OffsetDateTime now = OffsetDateTime.now();
        referral.setFollowupAttempts(referral.getFollowupAttempts() + 1);
        referral.setInvitedAt(now);
        referral.setActivationToken(null);
        referral.setTokenExpiresAt(null);
        PartnerReferral saved = referralRepository.save(referral);
        sendAuditEvent(Constants.CONVITE_RESEND, partnerId, saved.getId(), Constants.SUCCESS, null, correlationID);

        Partner partner = saved.getPartner();
        kafkaProducer.sendInviteRequested(new PartnerInviteRequestedEvent(
                saved.getId(),
                partnerId,
                partner.getName(),
                partner.getReferralCode(),
                saved.getTenantId(),
                saved.getCnpj(),
                null, null, null, null, null,
                now
        ));

        return saved;
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