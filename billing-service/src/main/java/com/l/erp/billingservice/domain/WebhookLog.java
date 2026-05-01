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

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "webhook_log", schema = "billing")
public class WebhookLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Size(max = 60)
    @NotNull
    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Size(max = 50)
    @Column(name = "asaas_payment_id", length = 50)
    private String asaasPaymentId;

    @Size(max = 50)
    @Column(name = "asaas_subscription_id", length = 50)
    private String asaasSubscriptionId;

    @NotNull
    @Column(name = "payload", nullable = false, length = Integer.MAX_VALUE)
    private String payload;

    @Size(max = 20)
    @NotNull
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", length = Integer.MAX_VALUE)
    private String errorMessage;

    @NotNull
    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;


}