package com.l.erp.emissaofiscalservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Idempotency-Key do {@code POST /emissao/documentos} — spec §3 item 10. Chave fornecida pelo
 * chamador (não derivada de pedidoId — o serviço precisa ser vendável separadamente, spec §2),
 * escopada por tenant. Ver {@link com.l.erp.emissaofiscalservice.services.documento.DocumentoFiscalService}
 * para a regra de fingerprint.
 */
@Entity
@Table(name = "idempotency_key", schema = "emissao")
@IdClass(IdempotencyKeyId.class)
public class IdempotencyKey {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Id
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "documento_id", nullable = false)
    private UUID documentoId;

    @Column(name = "fingerprint", nullable = false)
    private String fingerprint;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getDocumentoId() {
        return documentoId;
    }

    public void setDocumentoId(UUID documentoId) {
        this.documentoId = documentoId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
