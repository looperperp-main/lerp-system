package com.l.erp.partnerservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "partner", schema = "partner")
public class Partner {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Size(max = 200)
    @NotNull
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Size(max = 200)
    @NotNull
    @Column(name = "email", nullable = false, length = 200)
    private String email;

    @Size(max = 14)
    @NotNull
    @Column(name = "cnpj", nullable = false, length = 14)
    private String cnpj;

    @Size(max = 20)
    @Column(name = "crc", length = 20)
    private String crc;

    @Size(max = 20)
    @Column(name = "phone", length = 20)
    private String phone;

    @Size(max = 20)
    @NotNull
    @Column(name = "referral_code", nullable = false, length = 20)
    private String referralCode;

    @NotNull
    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Size(max = 30)
    @NotNull
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Size(max = 50)
    @Column(name = "asaas_customer_id", length = 50)
    private String asaasCustomerId;

    @Size(max = 100)
    @Column(name = "pix_key", length = 100)
    private String pixKey;

    @Size(max = 10)
    @Column(name = "pix_key_type", length = 10)
    private String pixKeyType;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Size(max = 200)
    @NotNull
    @Column(name = "created_by", nullable = false, length = 200)
    private String createdBy;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Size(max = 200)
    @Column(name = "updated_by", length = 200)
    private String updatedBy;

    @Size(max = 200)
    @Column(name = "reviewed_by", length = 200)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Size(max = 500)
    @Column(name = "review_notes", length = 500)
    private String reviewNotes;
}