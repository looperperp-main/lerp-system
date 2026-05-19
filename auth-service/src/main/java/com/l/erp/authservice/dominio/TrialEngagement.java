package com.l.erp.authservice.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(schema = "auth", name = "trial_engagement")
@Getter
@Setter
@NoArgsConstructor
public class TrialEngagement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "feature", nullable = false, length = 100)
    private String feature;

    @Column(name = "access_count", nullable = false)
    private int accessCount = 0;

    @Column(name = "first_accessed_at", nullable = false)
    private Instant firstAccessedAt;

    @Column(name = "last_accessed_at", nullable = false)
    private Instant lastAccessedAt;
}