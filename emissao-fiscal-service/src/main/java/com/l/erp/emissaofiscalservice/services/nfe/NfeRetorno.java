package com.l.erp.emissaofiscalservice.services.nfe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Retornos da SEFAZ (layout 4.00) que a emissão lê: só os campos que decidem estado. O resto do
 * XML (assinatura do protocolo, mensagens extras) é ignorado de propósito.
 */
public final class NfeRetorno {

    private NfeRetorno() {
    }

    /** {@code retEnviNFe}: cStat 103 = lote recebido (vem o recibo); 104 = lote já processado (indSinc=1). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RetEnviNFe(String cStat, String xMotivo, InfRec infRec, ProtNFe protNFe) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InfRec(String nRec, String tMed) {
    }

    /** {@code retConsReciNFe}: cStat 105 = em processamento; 104 = lote processado (vem o protNFe). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RetConsReciNFe(String cStat, String xMotivo, ProtNFe protNFe) {
    }

    /** {@code retConsSitNFe}: cStat 100/150 autorizada, 110/301/302/303 denegada, 217 não consta na base. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RetConsSitNFe(String cStat, String xMotivo, String chNFe, ProtNFe protNFe) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProtNFe(InfProt infProt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InfProt(String chNFe, String dhRecbto, String nProt, String cStat, String xMotivo) {
    }
}
