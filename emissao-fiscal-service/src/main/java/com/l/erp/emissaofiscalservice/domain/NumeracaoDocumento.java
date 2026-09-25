package com.l.erp.emissaofiscalservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Contador de numeração por (tenant, emitente, documento, série) — spec §3 item 3.
 * Sem {@link com.l.erp.emissaofiscalservice.repository.filter.BaseTenantEntity}/{@code @Filter} de
 * propósito: o próximo número é obtido via lock explícito ({@code SELECT ... FOR UPDATE}) numa
 * query que já filtra por tenant_id manualmente (ver NumeracaoDocumentoRepository) — o filtro do
 * Hibernate não muda o comportamento de um {@code SELECT ... FOR UPDATE} nativo.
 */
@Entity
@Table(name = "numeracao_documento", schema = "emissao")
public class NumeracaoDocumento {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "emitente_id", nullable = false)
    private UUID emitenteId;

    @Column(name = "documento", nullable = false)
    private String documento;

    @Column(name = "serie", nullable = false)
    private String serie;

    @Column(name = "ultimo_numero", nullable = false)
    private long ultimoNumero;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getEmitenteId() {
        return emitenteId;
    }

    public void setEmitenteId(UUID emitenteId) {
        this.emitenteId = emitenteId;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public String getSerie() {
        return serie;
    }

    public void setSerie(String serie) {
        this.serie = serie;
    }

    public long getUltimoNumero() {
        return ultimoNumero;
    }

    public void setUltimoNumero(long ultimoNumero) {
        this.ultimoNumero = ultimoNumero;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
