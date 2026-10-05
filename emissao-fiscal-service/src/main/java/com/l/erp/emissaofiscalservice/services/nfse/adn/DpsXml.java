package com.l.erp.emissaofiscalservice.services.nfse.adn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Modelo do XML da DPS 1.00 (Sistema Nacional NFS-e) escrito à mão — só o subconjunto emitido
 * (prestador, tomador opcional, serviço simples, ISSQN). A ordem dos componentes de cada record é a
 * ordem do XSD ({@code xsd/nfse/tiposComplexos_v1.00.xsd}); quem garante isso é o teste contra o XSD.
 * Valores já chegam formatados como {@code String} (cada campo tem a sua regra de casas decimais) e
 * campos {@code null} não saem no XML.
 */
final class DpsXml {

    private DpsXml() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonRootName("infDPS")
    record InfDPS(
            @JacksonXmlProperty(isAttribute = true) @JsonProperty("Id") String id,
            String tpAmb, String dhEmi, String verAplic, String serie,
            @JsonProperty("nDPS") String nDPS, String dCompet, String tpEmit, String cLocEmi,
            Prest prest, Toma toma, Serv serv, Valores valores) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Prest(@JsonProperty("CNPJ") String cnpj, @JsonProperty("IM") String im, String xNome, End end,
                 String fone, String email, RegTrib regTrib) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Toma(@JsonProperty("CNPJ") String cnpj, @JsonProperty("CPF") String cpf, String xNome, End end,
                String fone, String email) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record End(EndNac endNac, String xLgr, String nro, String xCpl, String xBairro) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record EndNac(String cMun, @JsonProperty("CEP") String cep) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RegTrib(String opSimpNac, String regApTribSN, String regEspTrib) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Serv(LocPrest locPrest, CServ cServ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record LocPrest(String cLocPrestacao) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CServ(String cTribNac, String cTribMun, String xDescServ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Valores(VServPrest vServPrest, Trib trib) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record VServPrest(String vServ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Trib(TribMun tribMun, TotTrib totTrib) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TribMun(String tribISSQN, String pAliq, String tpRetISSQN) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TotTrib(String indTotTrib) {
    }
}
