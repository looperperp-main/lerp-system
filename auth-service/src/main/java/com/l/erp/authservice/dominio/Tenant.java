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

    @NotNull
    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private EnumTenantStatus status;

    @Column(name = "slug", length = 20)
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

    @Size(max = 200)
    @Column(name = "nome_fantasia", length = 200)
    private String nomeFantasia;

    @Size(max = 20)
    @Column(name = "inscricao_estadual", length = 20)
    private String inscricaoEstadual;

    @Size(max = 200)
    @Column(name = "email", length = 200)
    private String email;

    @Size(max = 20)
    @Column(name = "telefone", length = 20)
    private String telefone;

    @Size(max = 200)
    @Column(name = "logradouro", length = 200)
    private String logradouro;

    @Size(max = 20)
    @Column(name = "numero", length = 20)
    private String numero;

    @Size(max = 100)
    @Column(name = "complemento", length = 100)
    private String complemento;

    @Size(max = 100)
    @Column(name = "bairro", length = 100)
    private String bairro;

    @Size(max = 100)
    @Column(name = "cidade", length = 100)
    private String cidade;

    @Size(max = 2)
    @Column(name = "uf", length = 2)
    private String uf;

    @Size(max = 9)
    @Column(name = "cep", length = 9)
    private String cep;

    @Size(max = 10)
    @Column(name = "ibge_codigo", length = 10)
    private String ibgeCodigo;

    @Column(name = "trial_started_at")
    private Instant trialStartedAt;

    @Column(name = "trial_expires_at")
    private Instant trialExpiresAt;

    @Size(max = 30)
    @Column(name = "plan_type", length = 30)
    private String planType;

    @Size(max = 50)
    @Column(name = "asaas_subscription_id", length = 50)
    private String asaasSubscriptionId;

}
