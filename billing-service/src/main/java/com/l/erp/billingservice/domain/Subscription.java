package com.l.erp.billingservice.domain;

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
@Table(name = "subscription", schema = "billing")
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Size(max = 30)
    @NotNull
    @Column(name = "plan_type", nullable = false, length = 30)
    private String planType;

    @Size(max = 30)
    @Column(name = "billing_cycle", length = 30)
    private String billingCycle;

    @Column(name = "value", precision = 10, scale = 2)
    private BigDecimal value;

    @Size(max = 30)
    @NotNull
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "trial_started_at")
    private OffsetDateTime trialStartedAt;

    @Column(name = "trial_expires_at")
    private OffsetDateTime trialExpiresAt;

    @Size(max = 50)
    @Column(name = "asaas_customer_id", length = 50)
    private String asaasCustomerId;

    @Size(max = 50)
    @Column(name = "asaas_subscription_id", length = 50)
    private String asaasSubscriptionId;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "next_due_date")
    private OffsetDateTime nextDueDate;

    @Column(name = "suspended_at")
    private OffsetDateTime suspendedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


}