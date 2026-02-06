package com.l.erp.authservice.dominio;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
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