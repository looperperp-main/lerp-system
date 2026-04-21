package com.l.erp.authservice.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.util.UUID;


@Getter
@Setter
@Entity
@Table(name = "owner_marker", schema = "auth")
public class OwnerMarker {
    @Id
    @Column(name = "user_id")
    private UUID Id;

    @MapsId
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ColumnDefault("false")
    @Column(name = "enabled")
    private Boolean enabled;


}