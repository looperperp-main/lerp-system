package com.l.erp.authservice.dominio;

import com.l.erp.authservice.dominio.enumerators.EnumTenantStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


import java.time.Instant;

@Entity
@Table(schema = "auth", name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "tenant_id")
//    private UserAccount usersAccount;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Size(max = 200)
    @NotNull
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Size(max = 14)
    @NotNull
    @Column(name = "cnpj", nullable = false, length = 14)
    private String cnpj;

    @Size(max = 30)
    @NotNull
    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private EnumTenantStatus status;

    @Column(name = "slug", length = 7)
    private String slug;

    @NotNull
    @Column(name = "creation_date", nullable = false)
    private Instant creationDate;

    @Size(max = 200)
    @NotNull
    @Column(name = "created_by", nullable = false, length = 200)
    private String createdBy;

    @Column(name = "update_date")
    private Instant updateDate;

    @Size(max = 200)
    @Column(name = "last_updated_by", length = 200)
    private String lastUpdatedBy;

}
