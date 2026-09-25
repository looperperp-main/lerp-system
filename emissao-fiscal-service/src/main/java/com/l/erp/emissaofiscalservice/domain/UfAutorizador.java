package com.l.erp.emissaofiscalservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Qual autorizador (normal e de contingência) atende cada UF, por documento — spec §3 item 12.
 * {@code autorizadorNormal}/{@code autorizadorContingencia} são código livre (ver
 * {@link WebserviceEndpoint}), resolvidos depois em {@link WebserviceEndpoint#getAutorizador()}.
 */
@Entity
@Table(name = "uf_autorizador", schema = "emissao")
public class UfAutorizador {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "uf", nullable = false, length = 2)
    private String uf;

    @Enumerated(EnumType.STRING)
    @Column(name = "documento", nullable = false)
    private TipoDocumentoFiscal documento;

    @Column(name = "autorizador_normal", nullable = false)
    private String autorizadorNormal;

    @Column(name = "autorizador_contingencia", nullable = false)
    private String autorizadorContingencia;

    @Column(name = "prazo_cancelamento_horas", nullable = false)
    private int prazoCancelamentoHoras;

    @Column(name = "vigente_de", nullable = false)
    private LocalDate vigenteDe;

    @Column(name = "vigente_ate")
    private LocalDate vigenteAte;

    public UUID getId() {
        return id;
    }

    public String getUf() {
        return uf;
    }

    public void setUf(String uf) {
        this.uf = uf;
    }

    public TipoDocumentoFiscal getDocumento() {
        return documento;
    }

    public void setDocumento(TipoDocumentoFiscal documento) {
        this.documento = documento;
    }

    public String getAutorizadorNormal() {
        return autorizadorNormal;
    }

    public void setAutorizadorNormal(String autorizadorNormal) {
        this.autorizadorNormal = autorizadorNormal;
    }

    public String getAutorizadorContingencia() {
        return autorizadorContingencia;
    }

    public void setAutorizadorContingencia(String autorizadorContingencia) {
        this.autorizadorContingencia = autorizadorContingencia;
    }

    public int getPrazoCancelamentoHoras() {
        return prazoCancelamentoHoras;
    }

    public void setPrazoCancelamentoHoras(int prazoCancelamentoHoras) {
        this.prazoCancelamentoHoras = prazoCancelamentoHoras;
    }

    public LocalDate getVigenteDe() {
        return vigenteDe;
    }

    public void setVigenteDe(LocalDate vigenteDe) {
        this.vigenteDe = vigenteDe;
    }

    public LocalDate getVigenteAte() {
        return vigenteAte;
    }

    public void setVigenteAte(LocalDate vigenteAte) {
        this.vigenteAte = vigenteAte;
    }
}
