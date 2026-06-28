package com.l.erp.authservice.dominio;

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

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "permission", schema = "auth")
public class Permission {
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Size(max = 120)
    @NotNull
    @Column(name = "code", nullable = false, length = 120)
    private String code;

    @Size(max = 60)
    @NotNull
    @Column(name = "domain", nullable = false, length = 60)
    private String domain;

    /** {@code TENANT} (atribuível pelo portal de tenant) ou {@code PLATFORM} (só admin Syax). */
    @Size(max = 10)
    @NotNull
    @ColumnDefault("'TENANT'")
    @Column(name = "scope", nullable = false, length = 10)
    private String scope;

    @Size(max = 500)
    @NotNull
    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_date", nullable = false)
    private Instant createdDate;

    @Size(max = 200)
    @NotNull
    @Column(name = "created_by", nullable = false, length = 200)
    private String createdBy;

    @Column(name = "last_update_date")
    private Instant lastUpdateDate;

    @Size(max = 200)
    @Column(name = "last_update_by", length = 200)
    private String lastUpdatedBy;


}