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
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "trial_engagement", schema = "billing")
public class TrialEngagement {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Size(max = 100)
    @NotNull
    @Column(name = "feature_key", nullable = false, length = 100)
    private String featureKey;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "access_count", nullable = false)
    private Integer accessCount;

    @Column(name = "first_accessed_at")
    private OffsetDateTime firstAccessedAt;

    @Column(name = "last_accessed_at")
    private OffsetDateTime lastAccessedAt;


}