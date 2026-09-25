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
 * URL de um serviço SOAP por autorizador/ambiente — spec §3 item 12: "Configuração de endpoint é
 * dado, não código". {@code autorizador} é código livre (ex. {@code SVRS}, {@code PROPRIO_MG},
 * {@code SVC-AN}) — de propósito não é enum: um estado novo aderindo à SVRS, ou trocando de
 * autorizador próprio pra SVRS, é mudança de dado (changeset), não de código.
 */
@Entity
@Table(name = "webservice_endpoint", schema = "emissao")
public class WebserviceEndpoint {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "documento", nullable = false)
    private TipoDocumentoFiscal documento;

    @Column(name = "autorizador", nullable = false)
    private String autorizador;

    @Enumerated(EnumType.STRING)
    @Column(name = "servico", nullable = false)
    private ServicoWebservice servico;

    @Column(name = "versao", nullable = false)
    private String versao;

    @Column(name = "url", nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "ambiente", nullable = false)
    private AmbienteEmissao ambiente;

    @Column(name = "vigente_de", nullable = false)
    private LocalDate vigenteDe;

    @Column(name = "vigente_ate")
    private LocalDate vigenteAte;

    public UUID getId() {
        return id;
    }

    public TipoDocumentoFiscal getDocumento() {
        return documento;
    }

    public void setDocumento(TipoDocumentoFiscal documento) {
        this.documento = documento;
    }

    public String getAutorizador() {
        return autorizador;
    }

    public void setAutorizador(String autorizador) {
        this.autorizador = autorizador;
    }

    public ServicoWebservice getServico() {
        return servico;
    }

    public void setServico(ServicoWebservice servico) {
        this.servico = servico;
    }

    public String getVersao() {
        return versao;
    }

    public void setVersao(String versao) {
        this.versao = versao;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public AmbienteEmissao getAmbiente() {
        return ambiente;
    }

    public void setAmbiente(AmbienteEmissao ambiente) {
        this.ambiente = ambiente;
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
