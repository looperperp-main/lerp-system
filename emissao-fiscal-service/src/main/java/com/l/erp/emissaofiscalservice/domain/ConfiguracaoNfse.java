package com.l.erp.emissaofiscalservice.domain;

import com.l.erp.emissaofiscalservice.repository.filter.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Como emitir NFS-e para um emitente: município do prestador e provedor (padrão ADN) — spec §4.4.
 * A escolha do provedor é por emitente/município, nunca feature flag global.
 */
@Entity
@Table(name = "configuracao_nfse", schema = "emissao")
public class ConfiguracaoNfse extends BaseTenantEntity {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "emitente_id", nullable = false)
    private UUID emitenteId;

    @Column(name = "municipio_ibge", nullable = false, length = 7)
    private String municipioIbge;

    @Enumerated(EnumType.STRING)
    @Column(name = "provedor", nullable = false)
    private CodigoProvedorNfse provedor = CodigoProvedorNfse.ADN;

    @Column(name = "inscricao_municipal")
    private String inscricaoMunicipal;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "last_updated_by")
    private UUID lastUpdatedBy;

    public UUID getId() {
        return id;
    }

    public UUID getEmitenteId() {
        return emitenteId;
    }

    public void setEmitenteId(UUID emitenteId) {
        this.emitenteId = emitenteId;
    }

    public String getMunicipioIbge() {
        return municipioIbge;
    }

    public void setMunicipioIbge(String municipioIbge) {
        this.municipioIbge = municipioIbge;
    }

    public CodigoProvedorNfse getProvedor() {
        return provedor;
    }

    public void setProvedor(CodigoProvedorNfse provedor) {
        this.provedor = provedor;
    }

    public String getInscricaoMunicipal() {
        return inscricaoMunicipal;
    }

    public void setInscricaoMunicipal(String inscricaoMunicipal) {
        this.inscricaoMunicipal = inscricaoMunicipal;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public UUID getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public void setLastUpdatedBy(UUID lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }
}
