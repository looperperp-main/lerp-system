package com.l.erp.partnerservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "partner_referral", schema = "partner")
public class PartnerReferral {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Size(max = 14)
    @Column(name = "cnpj", length = 14)
    private String cnpj;

    @Size(max = 200)
    @Column(name = "razao_social", length = 200)
    private String razaoSocial;

    @Size(max = 200)
    @Column(name = "email_contato", length = 200)
    private String emailContato;

    @Size(max = 30)
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "invited_at", nullable = false)
    private OffsetDateTime invitedAt;

    @Size(max = 512)
    @Column(name = "activation_token", length = 512)
    private String activationToken;

    @Column(name = "token_expires_at")
    private OffsetDateTime tokenExpiresAt;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "converted_at")
    private OffsetDateTime convertedAt;

    @Column(name = "lost_at")
    private OffsetDateTime lostAt;

    @ColumnDefault("0")
    @Column(name = "followup_attempts")
    private Integer followupAttempts;

    @Size(max = 50)
    @Column(name = "plano_sugerido", length = 50)
    private String planoSugerido;

    @Column(name = "trial_started_at")
    private OffsetDateTime trialStartedAt;

    @Column(name = "trial_expires_at")
    private OffsetDateTime trialExpiresAt;
}