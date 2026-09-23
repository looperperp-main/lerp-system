package com.l.erp.emissaofiscalservice.domain;

import com.l.erp.emissaofiscalservice.repository.filter.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "certificado_digital", schema = "emissao")
public class CertificadoDigital extends BaseTenantEntity {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "estabelecimento_id", nullable = false)
    private UUID estabelecimentoId;

    @Column(name = "certificado_cifrado", nullable = false)
    private byte[] certificadoCifrado;

    @Column(name = "senha_cifrada", nullable = false)
    private byte[] senhaCifrada;

    @Column(name = "kek_version", nullable = false)
    private short kekVersion;

    @Column(name = "cnpj_subject", nullable = false, length = 14)
    private String cnpjSubject;

    @Column(name = "certificado_valido_ate", nullable = false)
    private OffsetDateTime certificadoValidoAte;

    @Column(name = "alerta_enviado", nullable = false)
    private boolean alertaEnviado;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

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

    public UUID getEstabelecimentoId() {
        return estabelecimentoId;
    }

    public void setEstabelecimentoId(UUID estabelecimentoId) {
        this.estabelecimentoId = estabelecimentoId;
    }

    public byte[] getCertificadoCifrado() {
        return certificadoCifrado;
    }

    public void setCertificadoCifrado(byte[] certificadoCifrado) {
        this.certificadoCifrado = certificadoCifrado;
    }

    public byte[] getSenhaCifrada() {
        return senhaCifrada;
    }

    public void setSenhaCifrada(byte[] senhaCifrada) {
        this.senhaCifrada = senhaCifrada;
    }

    public short getKekVersion() {
        return kekVersion;
    }

    public void setKekVersion(short kekVersion) {
        this.kekVersion = kekVersion;
    }

    public String getCnpjSubject() {
        return cnpjSubject;
    }

    public void setCnpjSubject(String cnpjSubject) {
        this.cnpjSubject = cnpjSubject;
    }

    public OffsetDateTime getCertificadoValidoAte() {
        return certificadoValidoAte;
    }

    public void setCertificadoValidoAte(OffsetDateTime certificadoValidoAte) {
        this.certificadoValidoAte = certificadoValidoAte;
    }

    public boolean isAlertaEnviado() {
        return alertaEnviado;
    }

    public void setAlertaEnviado(boolean alertaEnviado) {
        this.alertaEnviado = alertaEnviado;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
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
