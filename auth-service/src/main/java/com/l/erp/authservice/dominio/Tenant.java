package com.l.erp.authservice.dominio;

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
    
    @Size(max = 200)
    @Column(name = "last_updated_by", length = 200)
    private String lastUpdatedBy;
    
    @Column(name = "update_date")
    private Instant updateDate;
    
    @Size(max = 200)
    @NotNull
    @Column(name = "created_by", nullable = false, length = 200)
    private String createdBy;
    
    @NotNull
    @Column(name = "creation_date", nullable = false)
    private Instant creationDate;

    @Column(name = "slug", length = 7)
    private String slug;
    
    @Size(max = 30)
    @NotNull
    @Column(name = "status", nullable = false, length = 30)
    private String status;
    
    @Size(max = 14)
    @NotNull
    @Column(name = "cnpj", nullable = false, length = 14)
    private String cnpj;

    @Size(max = 200)
    @NotNull
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;
}
