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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "documento_fiscal", schema = "emissao")
public class DocumentoFiscal extends BaseTenantEntity {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "estabelecimento_id", nullable = false)
    private UUID estabelecimentoId;

    @Column(name = "documento", nullable = false)
    private String documento;

    @Column(name = "modelo")
    private String modelo;

    @Column(name = "serie", nullable = false)
    private String serie;

    @Column(name = "numero", nullable = false)
    private long numero;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusDocumentoFiscal status;

    @Column(name = "ambiente", nullable = false)
    private String ambiente;

    @Column(name = "tp_emis", nullable = false)
    private String tpEmis = "1";

    @Column(name = "chave_acesso")
    private String chaveAcesso;

    @Column(name = "xml_assinado")
    private String xmlAssinado;

    @Column(name = "protocolo")
    private String protocolo;

    @Column(name = "ultima_mensagem_sefaz")
    private String ultimaMensagemSefaz;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_recebido", nullable = false)
    private String payloadRecebido;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

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

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public String getModelo() {
        return modelo;
    }

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }

    public String getSerie() {
        return serie;
    }

    public void setSerie(String serie) {
        this.serie = serie;
    }

    public long getNumero() {
        return numero;
    }

    public void setNumero(long numero) {
        this.numero = numero;
    }

    public StatusDocumentoFiscal getStatus() {
        return status;
    }

    public void setStatus(StatusDocumentoFiscal status) {
        this.status = status;
    }

    public String getAmbiente() {
        return ambiente;
    }

    public void setAmbiente(String ambiente) {
        this.ambiente = ambiente;
    }

    public String getTpEmis() {
        return tpEmis;
    }

    public void setTpEmis(String tpEmis) {
        this.tpEmis = tpEmis;
    }

    public String getChaveAcesso() {
        return chaveAcesso;
    }

    public void setChaveAcesso(String chaveAcesso) {
        this.chaveAcesso = chaveAcesso;
    }

    public String getXmlAssinado() {
        return xmlAssinado;
    }

    public void setXmlAssinado(String xmlAssinado) {
        this.xmlAssinado = xmlAssinado;
    }

    public String getProtocolo() {
        return protocolo;
    }

    public void setProtocolo(String protocolo) {
        this.protocolo = protocolo;
    }

    public String getUltimaMensagemSefaz() {
        return ultimaMensagemSefaz;
    }

    public void setUltimaMensagemSefaz(String ultimaMensagemSefaz) {
        this.ultimaMensagemSefaz = ultimaMensagemSefaz;
    }

    public String getPayloadRecebido() {
        return payloadRecebido;
    }

    public void setPayloadRecebido(String payloadRecebido) {
        this.payloadRecebido = payloadRecebido;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
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
