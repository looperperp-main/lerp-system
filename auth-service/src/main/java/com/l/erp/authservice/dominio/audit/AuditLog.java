package com.l.erp.authservice.dominio.audit;

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

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "audit_log", schema = "audit")
public class AuditLog {
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Size(max = 80)
    @NotNull
    @Column(name = "action", nullable = false, length = 80)
    private String action;

    @Size(max = 60)
    @Column(name = "target_type", length = 60)
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Size(max = 20)
    @NotNull
    @Column(name = "result", nullable = false, length = 20)
    private String result;

    @Column(name = "details_json", length = Integer.MAX_VALUE)
    private String detailsJson;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @NotNull
    @Column(name = "event_date", nullable = false)
    private Instant eventDate;


}