package com.l.erp.emissaofiscalservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Transactional outbox — spec §3 item 10. Escrito na mesma transação da mudança de estado do
 * documento; {@link com.l.erp.emissaofiscalservice.infra.kafka.OutboxPublisherJob} publica depois.
 */
@Entity
@Table(name = "outbox_evento", schema = "emissao")
public class OutboxEvento {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "documento_id", nullable = false)
    private UUID documentoId;

    @Column(name = "tipo_evento", nullable = false)
    private String tipoEvento;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "publicado_em")
    private OffsetDateTime publicadoEm;

    public UUID getId() {
        return id;
    }

    public UUID getDocumentoId() {
        return documentoId;
    }

    public void setDocumentoId(UUID documentoId) {
        this.documentoId = documentoId;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(String tipoEvento) {
        this.tipoEvento = tipoEvento;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(OffsetDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public OffsetDateTime getPublicadoEm() {
        return publicadoEm;
    }

    public void setPublicadoEm(OffsetDateTime publicadoEm) {
        this.publicadoEm = publicadoEm;
    }
}
