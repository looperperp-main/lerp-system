package com.l.erp.authservice.dominio;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;


@Getter
@Setter
@Entity
@Table(name = "owner_marker", schema = "auth")
public class OwnerMarker {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @Id
    private UserAccount user;

    @ColumnDefault("false")
    @Column(name = "enabled")
    private Boolean enabled;


}