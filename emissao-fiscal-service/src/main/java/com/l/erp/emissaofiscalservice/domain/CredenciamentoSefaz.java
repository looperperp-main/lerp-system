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
 * Credenciamento do tenant como emissor na SEFAZ, por emitente × UF × modelo — spec §3
 * item 11, último bullet em aberto. Processo externo e humano (o credenciamento em si acontece no
 * portal da SEFAZ); esta tabela só registra o resultado e vira o gate consultado antes de permitir
 * emissão em {@code PRODUCAO} — sem linha {@code CREDENCIADO}, nega antes de gastar número ou
 * tentar falar com a SEFAZ.
 */
@Entity
@Table(name = "credenciamento_sefaz", schema = "emissao")
public class CredenciamentoSefaz extends BaseTenantEntity {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "emitente_id", nullable = false)
    private UUID emitenteId;

    @Column(name = "uf", nullable = false, length = 2)
    private String uf;

    @Enumerated(EnumType.STRING)
    @Column(name = "modelo", nullable = false)
    private TipoDocumentoFiscal modelo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusCredenciamento status;

    @Column(name = "data_credenciamento")
    private OffsetDateTime dataCredenciamento;

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

    public String getUf() {
        return uf;
    }

    public void setUf(String uf) {
        this.uf = uf;
    }

    public TipoDocumentoFiscal getModelo() {
        return modelo;
    }

    public void setModelo(TipoDocumentoFiscal modelo) {
        this.modelo = modelo;
    }

    public StatusCredenciamento getStatus() {
        return status;
    }

    public void setStatus(StatusCredenciamento status) {
        this.status = status;
    }

    public OffsetDateTime getDataCredenciamento() {
        return dataCredenciamento;
    }

    public void setDataCredenciamento(OffsetDateTime dataCredenciamento) {
        this.dataCredenciamento = dataCredenciamento;
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
