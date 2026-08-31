package com.l.erp.partnerservice.services;

import com.l.erp.common.util.Constants;
import com.l.erp.partnerservice.api.dto.AssinaturaResumoDTO;
import com.l.erp.partnerservice.api.dto.ClienteDetalheResponseDTO;
import com.l.erp.partnerservice.api.dto.ConviteRequestDTO;
import com.l.erp.partnerservice.api.dto.DashboardResponseDTO;
import com.l.erp.partnerservice.api.dto.EngajamentoTenantDTO;
import com.l.erp.partnerservice.api.dto.ExtratoComissoesDTO;
import com.l.erp.partnerservice.api.dto.FollowupRequestDTO;
import com.l.erp.partnerservice.api.dto.IndicacoesPorContadorDTO;
import com.l.erp.partnerservice.api.dto.OrigemTenantDTO;
import com.l.erp.partnerservice.api.dto.PartnerRequestDTO;
import com.l.erp.partnerservice.api.dto.PartnerReviewDTO;
import com.l.erp.partnerservice.api.dto.PayoutInfoDTO;
import com.l.erp.partnerservice.domain.Partner;
import com.l.erp.partnerservice.domain.PartnerReferral;
import com.l.erp.partnerservice.domain.TrialEngagement;
import com.l.erp.partnerservice.infra.billing.BillingAssinaturaResumoDTO;
import com.l.erp.partnerservice.infra.billing.BillingClient;
import com.l.erp.partnerservice.infra.billing.BillingComissaoItemDTO;
import com.l.erp.partnerservice.infra.billing.BillingExtratoDTO;
import com.l.erp.partnerservice.infra.kafka.AuditProducerService;
import com.l.erp.partnerservice.infra.kafka.KafkaPartnerProducerService;
import com.l.erp.partnerservice.repository.PartnerReferralRepository;
import com.l.erp.partnerservice.repository.PartnerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartnerServiceTest {

    @Mock
    private PartnerRepository repository;

    @Mock
    private PartnerReferralRepository referralRepository;

    @Mock
    private KafkaPartnerProducerService kafkaProducer;

    @Mock
    private AuditProducerService auditProducer;

    @Mock
    private TrialEngagementService engagementService;

    @Mock
    private BillingClient billingClient;

    @InjectMocks
    private PartnerService partnerService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PARTNER_ID = UUID.randomUUID();

    @BeforeEach
    void setUpRequestContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(Constants.HEADER_USER_ID, USER_ID.toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDownRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private Partner buildPartner(String status) {
        Partner partner = new Partner();
        partner.setId(PARTNER_ID);
        partner.setName("Contador XYZ");
        partner.setEmail("contador@teste.com");
        partner.setCnpj("12345678000190");
        partner.setCrc("SP-123456");
        partner.setPhone("11999999999");
        partner.setReferralCode("CTR-00001");
        partner.setCommissionRate(Constants.DEFAULT_COMMISSION_RATE);
        partner.setStatus(status);
        partner.setCreatedAt(OffsetDateTime.now());
        partner.setCreatedBy("admin");
        return partner;
    }

    // ── findAll / findById ──────────────────────────────────────────────

    @Test
    void shouldFindAllWithoutStatusFilter() {
        Page<Partner> page = new PageImpl<>(List.of(buildPartner(Constants.STATUS_ATIVO)));
        when(repository.findAll(any(Pageable.class))).thenReturn(page);

        Page<Partner> result = partnerService.findAll(null, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        verify(repository, never()).findByStatus(any(), any());
    }

    @Test
    void shouldFindAllWithStatusFilter() {
        Page<Partner> page = new PageImpl<>(List.of(buildPartner(Constants.STATUS_PENDENTE)));
        when(repository.findByStatus(eq(Constants.STATUS_PENDENTE), any(Pageable.class))).thenReturn(page);

        Page<Partner> result = partnerService.findAll(Constants.STATUS_PENDENTE, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        verify(repository, never()).findAll(any(Pageable.class));
    }

    @Test
    void shouldFindByIdSuccess() {
        Partner partner = buildPartner(Constants.STATUS_ATIVO);
        when(repository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        Partner result = partnerService.findById(PARTNER_ID);

        assertThat(result.getId()).isEqualTo(PARTNER_ID);
    }

    @Test
    void shouldThrowNotFoundWhenFindByIdMissing() {
        when(repository.findById(PARTNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partnerService.findById(PARTNER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("não encontrado");
    }

    // ── PayoutInfo ───────────────────────────────────────────────────────

    @Test
    void shouldGetPayoutInfo() {
        Partner partner = buildPartner(Constants.STATUS_ATIVO);
        partner.setPixKey("chave@pix.com");
        partner.setPixKeyType("EMAIL");
        when(repository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        PayoutInfoDTO dto = partnerService.getPayoutInfo(PARTNER_ID);

        assertThat(dto.pixKey()).isEqualTo("chave@pix.com");
        assertThat(dto.pixKeyType()).isEqualTo("EMAIL");
    }

    @Test
    void shouldUpdatePayoutInfo() {
        Partner partner = buildPartner(Constants.STATUS_ATIVO);
        when(repository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));
        when(repository.save(any(Partner.class))).thenAnswer(inv -> inv.getArgument(0));

        PayoutInfoDTO dto = partnerService.updatePayoutInfo(PARTNER_ID,
                new PayoutInfoDTO("11988887777", "PHONE"), "admin");

        assertThat(dto.pixKey()).isEqualTo("11988887777");
        assertThat(dto.pixKeyType()).isEqualTo("PHONE");
        assertThat(partner.getUpdatedBy()).isEqualTo("admin");
        verify(repository).save(partner);
    }

    // ── save ─────────────────────────────────────────────────────────────

    @Test
    void shouldSavePartnerSuccess() {
        PartnerRequestDTO dto = new PartnerRequestDTO("Contador XYZ", "SP-123456", "12345678000190",
                "contador@teste.com", "11999999999", null, null);
        when(repository.existsByCnpj(dto.cnpj())).thenReturn(false);
        when(repository.existsByEmail(dto.email())).thenReturn(false);
        when(repository.save(any(Partner.class))).thenAnswer(inv -> {
            Partner p = inv.getArgument(0);
            p.setId(PARTNER_ID);
            return p;
        });

        Partner saved = partnerService.save(dto, "admin");

        assertThat(saved.getId()).isEqualTo(PARTNER_ID);
        assertThat(saved.getStatus()).isEqualTo(Constants.STATUS_PENDENTE);
        assertThat(saved.getCommissionRate()).isEqualTo(Constants.DEFAULT_COMMISSION_RATE);
        verify(auditProducer).sendAuditEvent(any());
    }

    @Test
    void shouldRejectSaveWhenCnpjAlreadyExists() {
        PartnerRequestDTO dto = new PartnerRequestDTO("Contador XYZ", "SP-123456", "12345678000190",
                "contador@teste.com", "11999999999", null, null);
        when(repository.existsByCnpj(dto.cnpj())).thenReturn(true);

        assertThatThrownBy(() -> partnerService.save(dto, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(Constants.PARCEIRO_CNPJ_ALREADY_EXISTS);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectSaveWhenEmailAlreadyExists() {
        PartnerRequestDTO dto = new PartnerRequestDTO("Contador XYZ", "SP-123456", "12345678000190",
                "contador@teste.com", "11999999999", null, null);
        when(repository.existsByCnpj(dto.cnpj())).thenReturn(false);
        when(repository.existsByEmail(dto.email())).thenReturn(true);

        assertThatThrownBy(() -> partnerService.save(dto, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(Constants.PARCEIRO_EMAIL_ALREADY_EXISTS);
        verify(repository, never()).save(any());
    }

    // ── update ───────────────────────────────────────────────────────────

    @Test
    void shouldUpdatePartnerSuccess() {
        Partner existing = buildPartner(Constants.STATUS_ATIVO);
        PartnerRequestDTO dto = new PartnerRequestDTO("Novo Nome", "SP-999999", existing.getCnpj(),
                existing.getEmail(), "11988887777", null, null);
        when(repository.findById(PARTNER_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any(Partner.class))).thenAnswer(inv -> inv.getArgument(0));

        Partner updated = partnerService.update(PARTNER_ID, dto, "admin");

        assertThat(updated.getName()).isEqualTo("Novo Nome");
        assertThat(updated.getPhone()).isEqualTo("11988887777");
        verify(auditProducer).sendAuditEvent(any());
    }

    @Test
    void shouldThrowNotFoundWhenUpdatingMissingPartner() {
        PartnerRequestDTO dto = new PartnerRequestDTO("Novo Nome", null, "12345678000190",
                "contador@teste.com", null, null, null);
        when(repository.findById(PARTNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partnerService.update(PARTNER_ID, dto, "admin"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldRejectUpdateWhenCnpjBelongsToAnotherPartner() {
        Partner existing = buildPartner(Constants.STATUS_ATIVO);
        PartnerRequestDTO dto = new PartnerRequestDTO(existing.getName(), existing.getCrc(), "99999999000199",
                existing.getEmail(), existing.getPhone(), null, null);
        when(repository.findById(PARTNER_ID)).thenReturn(Optional.of(existing));
        when(repository.existsByCnpj("99999999000199")).thenReturn(true);

        assertThatThrownBy(() -> partnerService.update(PARTNER_ID, dto, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(Constants.PARCEIRO_CNPJ_ALREADY_EXISTS);
    }

    @Test
    void shouldRejectUpdateWhenEmailBelongsToAnotherPartner() {
        Partner existing = buildPartner(Constants.STATUS_ATIVO);
        PartnerRequestDTO dto = new PartnerRequestDTO(existing.getName(), existing.getCrc(), existing.getCnpj(),
                "outro@teste.com", existing.getPhone(), null, null);
        when(repository.findById(PARTNER_ID)).thenReturn(Optional.of(existing));
        when(repository.existsByEmail("outro@teste.com")).thenReturn(true);

        assertThatThrownBy(() -> partnerService.update(PARTNER_ID, dto, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(Constants.PARCEIRO_EMAIL_ALREADY_EXISTS);
    }

    // ── approve / reject / inactivate ───────────────────────────────────

    @Test
    void shouldApprovePendingPartner() {
        Partner partner = buildPartner(Constants.STATUS_PENDENTE);
        when(repository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));
        when(repository.save(any(Partner.class))).thenAnswer(inv -> inv.getArgument(0));

        Partner approved = partnerService.approve(PARTNER_ID, new PartnerReviewDTO("ok"), "admin");

        assertThat(approved.getStatus()).isEqualTo(Constants.STATUS_ATIVO);
        assertThat(approved.getReviewedBy()).isEqualTo("admin");
        verify(kafkaProducer).sendPartnerApproved(any());
    }

    @Test
    void shouldRejectApproveWhenPartnerNotPending() {
        Partner partner = buildPartner(Constants.STATUS_ATIVO);
        when(repository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertThatThrownBy(() -> partnerService.approve(PARTNER_ID, new PartnerReviewDTO("ok"), "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
        verify(kafkaProducer, never()).sendPartnerApproved(any());
    }

    @Test
    void shouldInactivateActivePartner() {
        Partner partner = buildPartner(Constants.STATUS_ATIVO);
        when(repository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));
        when(repository.save(any(Partner.class))).thenAnswer(inv -> inv.getArgument(0));

        Partner result = partnerService.inactivate(PARTNER_ID, "admin");

        assertThat(result.getStatus()).isEqualTo(Constants.STATUS_INATIVO);
        verify(kafkaProducer).sendPartnerInactivated(PARTNER_ID);
    }

    @Test
    void shouldRejectInactivateWhenNotActive() {
        Partner partner = buildPartner(Constants.STATUS_PENDENTE);
        when(repository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertThatThrownBy(() -> partnerService.inactivate(PARTNER_ID, "admin"))
                .isInstanceOf(ResponseStatusException.class);
        verify(kafkaProducer, never()).sendPartnerInactivated(any());
    }

    @Test
    void shouldRejectPendingPartner() {
        Partner partner = buildPartner(Constants.STATUS_PENDENTE);
        when(repository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));
        when(repository.save(any(Partner.class))).thenAnswer(inv -> inv.getArgument(0));

        Partner result = partnerService.reject(PARTNER_ID, new PartnerReviewDTO("motivo"), "admin");

        assertThat(result.getStatus()).isEqualTo(Constants.STATUS_REPROVADO);
        assertThat(result.getReviewNotes()).isEqualTo("motivo");
    }

    @Test
    void shouldRejectRejectWhenPartnerNotPending() {
        Partner partner = buildPartner(Constants.STATUS_ATIVO);
        when(repository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertThatThrownBy(() -> partnerService.reject(PARTNER_ID, new PartnerReviewDTO("motivo"), "admin"))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ── convites ─────────────────────────────────────────────────────────

    @Test
    void shouldEnviarConviteSuccess() {
        Partner partner = buildPartner(Constants.STATUS_ATIVO);
        ConviteRequestDTO dto = new ConviteRequestDTO("11222333000181", "Cliente LTDA", null,
                "cliente@teste.com", null, "BASICO");
        when(repository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));
        when(referralRepository.existsByPartner_IdAndCnpjAndStatusIn(eq(PARTNER_ID), eq(dto.cnpj()), anyList()))
                .thenReturn(false);
        when(referralRepository.save(any(PartnerReferral.class))).thenAnswer(inv -> {
            PartnerReferral r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        PartnerReferral result = partnerService.enviarConvite(PARTNER_ID, dto);

        assertThat(result.getStatus()).isEqualTo(Constants.CONVIDADO);
        assertThat(result.getCnpj()).isEqualTo(dto.cnpj());
        verify(kafkaProducer).sendInviteRequested(any());
    }

    @Test
    void shouldRejectEnviarConviteWhenPartnerNotFound() {
        when(repository.findById(PARTNER_ID)).thenReturn(Optional.empty());
        ConviteRequestDTO dto = new ConviteRequestDTO("11222333000181", "Cliente LTDA", null,
                "cliente@teste.com", null, "BASICO");

        assertThatThrownBy(() -> partnerService.enviarConvite(PARTNER_ID, dto))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldRejectEnviarConviteWhenPartnerNotActive() {
        Partner partner = buildPartner(Constants.STATUS_PENDENTE);
        when(repository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));
        ConviteRequestDTO dto = new ConviteRequestDTO("11222333000181", "Cliente LTDA", null,
                "cliente@teste.com", null, "BASICO");

        assertThatThrownBy(() -> partnerService.enviarConvite(PARTNER_ID, dto))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldRejectEnviarConviteWhenActiveConviteAlreadyExists() {
        Partner partner = buildPartner(Constants.STATUS_ATIVO);
        ConviteRequestDTO dto = new ConviteRequestDTO("11222333000181", "Cliente LTDA", null,
                "cliente@teste.com", null, "BASICO");
        when(repository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));
        when(referralRepository.existsByPartner_IdAndCnpjAndStatusIn(eq(PARTNER_ID), eq(dto.cnpj()), anyList()))
                .thenReturn(true);

        assertThatThrownBy(() -> partnerService.enviarConvite(PARTNER_ID, dto))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void shouldListarConvites() {
        PartnerReferral referral = new PartnerReferral();
        referral.setId(UUID.randomUUID());
        referral.setCnpj("11222333000181");
        referral.setRazaoSocial("Cliente LTDA");
        referral.setStatus(Constants.CONVIDADO);
        referral.setFollowupAttempts(0);
        referral.setInvitedAt(OffsetDateTime.now());
        when(referralRepository.findByPartner_Id(eq(PARTNER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(referral)));

        Page<?> result = partnerService.listarConvites(PARTNER_ID, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
    }

    // ── iniciarFollowup ──────────────────────────────────────────────────

    private PartnerReferral buildReferral(String status, int followupAttempts) {
        PartnerReferral referral = new PartnerReferral();
        referral.setId(UUID.randomUUID());
        referral.setPartner(buildPartner(Constants.STATUS_ATIVO));
        referral.setCnpj("11222333000181");
        referral.setRazaoSocial("Cliente LTDA");
        referral.setEmailContato("cliente@teste.com");
        referral.setStatus(status);
        referral.setFollowupAttempts(followupAttempts);
        referral.setInvitedAt(OffsetDateTime.now());
        return referral;
    }

    @Test
    void shouldIniciarFollowupSuccess() {
        UUID referralId = UUID.randomUUID();
        PartnerReferral referral = buildReferral(Constants.FOLLOWUP, 0);
        when(referralRepository.findByIdAndPartner_Id(referralId, PARTNER_ID)).thenReturn(Optional.of(referral));

        partnerService.iniciarFollowup(referralId, PARTNER_ID, new FollowupRequestDTO("mensagem"));

        assertThat(referral.getFollowupAttempts()).isEqualTo(1);
        assertThat(referral.getStatus()).isEqualTo(Constants.FOLLOWUP);
        verify(kafkaProducer).sendEmailNotification(any());
        verify(referralRepository).save(referral);
    }

    @Test
    void shouldRejectIniciarFollowupWhenStatusNotFollowup() {
        UUID referralId = UUID.randomUUID();
        PartnerReferral referral = buildReferral(Constants.CONVIDADO, 0);
        when(referralRepository.findByIdAndPartner_Id(referralId, PARTNER_ID)).thenReturn(Optional.of(referral));

        assertThatThrownBy(() -> partnerService.iniciarFollowup(referralId, PARTNER_ID, new FollowupRequestDTO("mensagem")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldMarkReferralAsPerdidoAfterThirdFollowupAttempt() {
        UUID referralId = UUID.randomUUID();
        PartnerReferral referral = buildReferral(Constants.FOLLOWUP, 2);
        when(referralRepository.findByIdAndPartner_Id(referralId, PARTNER_ID)).thenReturn(Optional.of(referral));

        partnerService.iniciarFollowup(referralId, PARTNER_ID, new FollowupRequestDTO("mensagem"));

        assertThat(referral.getFollowupAttempts()).isEqualTo(3);
        assertThat(referral.getStatus()).isEqualTo(Constants.PERDIDO);
        // uma notificação de follow-up + uma de PERDIDO
        verify(kafkaProducer, times(2)).sendEmailNotification(any());
    }

    // ── reenviarConvite ──────────────────────────────────────────────────

    @Test
    void shouldReenviarConviteSuccess() {
        UUID referralId = UUID.randomUUID();
        PartnerReferral referral = buildReferral(Constants.CONVIDADO, 1);
        referral.setTenantId(42L);
        when(referralRepository.findByIdAndPartner_Id(referralId, PARTNER_ID)).thenReturn(Optional.of(referral));
        when(referralRepository.save(any(PartnerReferral.class))).thenAnswer(inv -> inv.getArgument(0));

        PartnerReferral result = partnerService.reenviarConvite(referralId, PARTNER_ID);

        assertThat(result.getFollowupAttempts()).isEqualTo(2);
        verify(kafkaProducer).sendInviteRequested(any());
    }

    @Test
    void shouldRejectReenviarConviteWhenNotFound() {
        UUID referralId = UUID.randomUUID();
        when(referralRepository.findByIdAndPartner_Id(referralId, PARTNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partnerService.reenviarConvite(referralId, PARTNER_ID))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldRejectReenviarConviteWhenStatusNotConvidado() {
        UUID referralId = UUID.randomUUID();
        PartnerReferral referral = buildReferral(Constants.ATIVADO, 0);
        referral.setTenantId(42L);
        when(referralRepository.findByIdAndPartner_Id(referralId, PARTNER_ID)).thenReturn(Optional.of(referral));

        assertThatThrownBy(() -> partnerService.reenviarConvite(referralId, PARTNER_ID))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldRejectReenviarConviteWhenTenantStillProcessing() {
        UUID referralId = UUID.randomUUID();
        PartnerReferral referral = buildReferral(Constants.CONVIDADO, 0);
        referral.setTenantId(null);
        when(referralRepository.findByIdAndPartner_Id(referralId, PARTNER_ID)).thenReturn(Optional.of(referral));

        assertThatThrownBy(() -> partnerService.reenviarConvite(referralId, PARTNER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ── dashboard / comissões ────────────────────────────────────────────

    @Test
    void shouldGetDashboardWithBillingData() {
        when(referralRepository.countByPartner_IdAndStatus(eq(PARTNER_ID), any())).thenReturn(1L);
        when(referralRepository.countByPartner_IdAndStatusIn(eq(PARTNER_ID), anyList())).thenReturn(2L);
        when(referralRepository.countByPartner_IdAndStatusAndTrialExpiresAtBefore(eq(PARTNER_ID), any(), any())).thenReturn(1L);
        when(referralRepository.findTop10ByPartner_IdAndStatusOrderByTrialExpiresAtAsc(eq(PARTNER_ID), any())).thenReturn(List.of());
        when(referralRepository.findTop10ByPartner_IdOrderByInvitedAtDesc(PARTNER_ID)).thenReturn(List.of());
        when(referralRepository.findTop10ByPartner_IdAndActivatedAtNotNullOrderByActivatedAtDesc(PARTNER_ID)).thenReturn(List.of());
        when(billingClient.getExtrato(PARTNER_ID)).thenReturn(new BillingExtratoDTO(
                BigDecimal.TEN, BigDecimal.valueOf(50), BigDecimal.ONE, "2026-06", OffsetDateTime.now(), 5, List.of()));

        DashboardResponseDTO dashboard = partnerService.getDashboard(PARTNER_ID);

        assertThat(dashboard.comissaoMesAtual()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(dashboard.totalComissoesPagas()).isEqualByComparingTo(BigDecimal.valueOf(50));
    }

    @Test
    void shouldGetDashboardFallbackToZeroWhenBillingFails() {
        when(referralRepository.countByPartner_IdAndStatus(eq(PARTNER_ID), any())).thenReturn(0L);
        when(referralRepository.countByPartner_IdAndStatusIn(eq(PARTNER_ID), anyList())).thenReturn(0L);
        when(referralRepository.countByPartner_IdAndStatusAndTrialExpiresAtBefore(eq(PARTNER_ID), any(), any())).thenReturn(0L);
        when(referralRepository.findTop10ByPartner_IdAndStatusOrderByTrialExpiresAtAsc(eq(PARTNER_ID), any())).thenReturn(List.of());
        when(referralRepository.findTop10ByPartner_IdOrderByInvitedAtDesc(PARTNER_ID)).thenReturn(List.of());
        when(referralRepository.findTop10ByPartner_IdAndActivatedAtNotNullOrderByActivatedAtDesc(PARTNER_ID)).thenReturn(List.of());
        when(billingClient.getExtrato(PARTNER_ID)).thenThrow(new IllegalStateException("billing indisponível"));

        DashboardResponseDTO dashboard = partnerService.getDashboard(PARTNER_ID);

        assertThat(dashboard.comissaoMesAtual()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dashboard.totalComissoesPagas()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldGetComissoesEnrichedWithReferralData() {
        PartnerReferral referral = buildReferral(Constants.CONVERTIDO, 0);
        referral.setTenantId(42L);
        when(referralRepository.findByPartner_Id(eq(PARTNER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(referral)));

        BillingComissaoItemDTO item = new BillingComissaoItemDTO(UUID.randomUUID(), 42L,
                BigDecimal.TEN, BigDecimal.valueOf(100), BigDecimal.TEN, "PADRAO", "BASICO",
                "2026-06", "PAGO", OffsetDateTime.now(), OffsetDateTime.now());
        when(billingClient.getExtrato(PARTNER_ID)).thenReturn(new BillingExtratoDTO(
                BigDecimal.TEN, BigDecimal.valueOf(50), BigDecimal.ONE, "2026-06", OffsetDateTime.now(), 5,
                List.of(item)));

        ExtratoComissoesDTO result = partnerService.getComissoes(PARTNER_ID);

        assertThat(result.historico()).hasSize(1);
        assertThat(result.historico().getFirst().razaoSocial()).isEqualTo("Cliente LTDA");
        assertThat(result.historico().getFirst().cnpj()).isEqualTo(referral.getCnpj());
    }

    @Test
    void shouldGetComissoesWithoutMatchingReferralUsesPlaceholder() {
        when(referralRepository.findByPartner_Id(eq(PARTNER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        BillingComissaoItemDTO item = new BillingComissaoItemDTO(UUID.randomUUID(), 999L,
                BigDecimal.TEN, BigDecimal.valueOf(100), BigDecimal.TEN, "PADRAO", "BASICO",
                "2026-06", "PAGO", OffsetDateTime.now(), OffsetDateTime.now());
        when(billingClient.getExtrato(PARTNER_ID)).thenReturn(new BillingExtratoDTO(
                BigDecimal.TEN, BigDecimal.valueOf(50), BigDecimal.ONE, "2026-06", OffsetDateTime.now(), 5,
                List.of(item)));

        ExtratoComissoesDTO result = partnerService.getComissoes(PARTNER_ID);

        assertThat(result.historico().getFirst().razaoSocial()).isEqualTo("—");
        assertThat(result.historico().getFirst().cnpj()).isEqualTo("—");
    }

    // ── cliente detalhe / assinatura ─────────────────────────────────────

    @Test
    void shouldGetClienteDetalheWithoutTenant() {
        UUID referralId = UUID.randomUUID();
        PartnerReferral referral = buildReferral(Constants.CONVIDADO, 0);
        referral.setTenantId(null);
        when(referralRepository.findByIdAndPartner_Id(referralId, PARTNER_ID)).thenReturn(Optional.of(referral));

        ClienteDetalheResponseDTO result = partnerService.getClienteDetalhe(referralId, PARTNER_ID);

        assertThat(result.loginCount()).isZero();
        assertThat(result.daysActive()).isZero();
        verify(engagementService, never()).getEngagement(any());
    }

    @Test
    void shouldThrowNotFoundWhenClienteDetalheReferralMissing() {
        UUID referralId = UUID.randomUUID();
        when(referralRepository.findByIdAndPartner_Id(referralId, PARTNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partnerService.getClienteDetalhe(referralId, PARTNER_ID))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldGetAssinaturaReturnsEmptyWhenNoTenant() {
        UUID referralId = UUID.randomUUID();
        PartnerReferral referral = buildReferral(Constants.CONVIDADO, 0);
        referral.setTenantId(null);
        when(referralRepository.findByIdAndPartner_Id(referralId, PARTNER_ID)).thenReturn(Optional.of(referral));

        AssinaturaResumoDTO result = partnerService.getAssinatura(referralId, PARTNER_ID);

        assertThat(result.status()).isNull();
        verify(billingClient, never()).getAssinaturaResumo(any());
    }

    @Test
    void shouldGetAssinaturaWithTenant() {
        UUID referralId = UUID.randomUUID();
        PartnerReferral referral = buildReferral(Constants.CONVERTIDO, 0);
        referral.setTenantId(42L);
        when(referralRepository.findByIdAndPartner_Id(referralId, PARTNER_ID)).thenReturn(Optional.of(referral));
        when(billingClient.getAssinaturaResumo(42L)).thenReturn(new BillingAssinaturaResumoDTO(
                "ATIVO", "EM_DIA", BigDecimal.valueOf(199), "PIX", OffsetDateTime.now(), OffsetDateTime.now(),
                BigDecimal.TEN, "2026-06"));

        AssinaturaResumoDTO result = partnerService.getAssinatura(referralId, PARTNER_ID);

        assertThat(result.status()).isEqualTo("ATIVO");
        assertThat(result.value()).isEqualByComparingTo(BigDecimal.valueOf(199));
    }

    // ── relatórios administrativos ───────────────────────────────────────

    @Test
    void shouldListIndicacoesPorContador() {
        PartnerReferral referral = buildReferral(Constants.CONVERTIDO, 0);
        when(referralRepository.findAllWithPartner()).thenReturn(List.of(referral));

        List<IndicacoesPorContadorDTO> result = partnerService.listIndicacoesPorContador();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().totalIndicacoes()).isEqualTo(1);
        assertThat(result.getFirst().convertidas()).isEqualTo(1);
    }

    @Test
    void shouldGetOrigemPorTenantWhenPresent() {
        PartnerReferral referral = buildReferral(Constants.CONVERTIDO, 0);
        when(referralRepository.findByTenantIdWithPartner(42L)).thenReturn(List.of(referral));

        Optional<OrigemTenantDTO> result = partnerService.getOrigemPorTenant(42L);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(Constants.CONVERTIDO);
    }

    @Test
    void shouldGetOrigemPorTenantWhenAbsent() {
        when(referralRepository.findByTenantIdWithPartner(42L)).thenReturn(List.of());

        Optional<OrigemTenantDTO> result = partnerService.getOrigemPorTenant(42L);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldGetEngajamentoPorTenantWithReferral() {
        PartnerReferral referral = buildReferral(Constants.CONVERTIDO, 0);
        referral.setTrialStartedAt(OffsetDateTime.now().minusDays(5));
        when(referralRepository.findByTenantIdWithPartner(42L)).thenReturn(List.of(referral));

        TrialEngagement login = new TrialEngagement();
        login.setAccessCount(3);
        login.setLastAccessedAt(OffsetDateTime.now());
        when(engagementService.getLoginStats(42L)).thenReturn(Optional.of(login));

        EngajamentoTenantDTO result = partnerService.getEngajamentoPorTenant(42L);

        assertThat(result.loginCount()).isEqualTo(3);
        assertThat(result.lastLoginAt()).isNotNull();
        assertThat(result.daysActive()).isEqualTo(5);
    }

    @Test
    void shouldGetEngajamentoPorTenantWithoutReferral() {
        when(referralRepository.findByTenantIdWithPartner(42L)).thenReturn(List.of());

        EngajamentoTenantDTO result = partnerService.getEngajamentoPorTenant(42L);

        assertThat(result.loginCount()).isZero();
        assertThat(result.daysActive()).isZero();
    }
}
