package com.l.erp.emissaofiscalservice.services.nfse.adn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Pedido de registro de evento 1.00 — só o cancelamento (e101101). Ordem dos componentes = ordem do
 * XSD ({@code xsd/nfse/tiposEventos_v1.00.xsd}, {@code TCInfPedReg}); o teste valida contra o XSD.
 */
final class EventoXml {

    private EventoXml() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonRootName("infPedReg")
    record InfPedReg(
            @JacksonXmlProperty(isAttribute = true) @JsonProperty("Id") String id,
            String tpAmb, String verAplic, String dhEvento,
            @JsonProperty("CNPJAutor") String cnpjAutor,
            @JsonProperty("chNFSe") String chNFSe,
            String nPedRegEvento,
            @JsonProperty("e101101") E101101 e101101) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record E101101(String xDesc, String cMotivo, String xMotivo) {
    }
}
