package com.l.erp.emissaofiscalservice.services.nfe;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;

/**
 * Modelo do XML da NF-e 4.00 (PL 010f) escrito à mão — só o subconjunto emitido nesta etapa
 * (emitente CRT 3, produto, ICMS 00/20/40, PIS/COFINS, IBS/CBS). A ordem dos elementos é a ordem de
 * declaração dos componentes de cada record e espelha o XSD ({@code xsd/nfe/leiauteNFe_v4.00.xsd});
 * quem garante isso é a validação contra o XSD em {@code NfeXmlBuilderTest}, não este arquivo.
 *
 * <p>Valores numéricos são {@code String} já formatada pelo {@link NfeXmlBuilder} porque cada campo do
 * leiaute tem sua própria regra de casas decimais (13v2, 3v2-4, 11v0-4...). Campos {@code null} não
 * saem no XML ({@code NON_NULL}) — é assim que os grupos opcionais e as escolhas (ICMS00 ou ICMS20...) ficam
 * de fora.</p>
 */
public final class NfeXml {

    private NfeXml() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonRootName("infNFe")
    public record InfNFe(
            @JacksonXmlProperty(isAttribute = true) @JsonProperty("versao") String versao,
            @JacksonXmlProperty(isAttribute = true) @JsonProperty("Id") String id,
            @JsonProperty("ide") Ide ide,
            @JsonProperty("emit") Emit emit,
            @JsonProperty("dest") Dest dest,
            @JacksonXmlElementWrapper(useWrapping = false) @JsonProperty("det") List<Det> det,
            @JsonProperty("total") Total total,
            @JsonProperty("transp") Transp transp,
            @JsonProperty("pag") Pag pag) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Ide(String cUF, String cNF, String natOp, String mod, String serie, String nNF, String dhEmi,
                      String tpNF, String idDest, String cMunFG, String tpImp, String tpEmis, String cDV,
                      String tpAmb, String finNFe, String indFinal, String indPres, String procEmi,
                      String verProc) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Emit(
            @JsonProperty("CNPJ") String cnpj,
            @JsonProperty("xNome") String xNome,
            @JsonProperty("xFant") String xFant,
            @JsonProperty("enderEmit") Endereco enderEmit,
            @JsonProperty("IE") String ie,
            @JsonProperty("CRT") String crt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Dest(
            @JsonProperty("CNPJ") String cnpj,
            @JsonProperty("CPF") String cpf,
            @JsonProperty("xNome") String xNome,
            @JsonProperty("enderDest") Endereco enderDest,
            @JsonProperty("indIEDest") String indIEDest,
            @JsonProperty("IE") String ie,
            @JsonProperty("email") String email) {
    }

    /** Serve para enderEmit (TEnderEmi) e enderDest (TEndereco) — mesma ordem de elementos. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Endereco(String xLgr, String nro, String xCpl, String xBairro, String cMun, String xMun,
                           @JsonProperty("UF") String uf, @JsonProperty("CEP") String cep, String fone) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Det(
            @JacksonXmlProperty(isAttribute = true) @JsonProperty("nItem") String nItem,
            @JsonProperty("prod") Prod prod,
            @JsonProperty("imposto") Imposto imposto) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Prod(
            String cProd, String cEAN, String xProd,
            @JsonProperty("NCM") String ncm,
            @JsonProperty("CFOP") String cfop,
            String uCom, String qCom, String vUnCom, String vProd, String cEANTrib, String uTrib,
            String qTrib, String vUnTrib, String indTot) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Imposto(
            @JsonProperty("ICMS") Icms icms,
            @JsonProperty("PIS") Pis pis,
            @JsonProperty("COFINS") Cofins cofins,
            @JsonProperty("IBSCBS") IbsCbs ibsCbs) {
    }

    /** Escolha (choice) do XSD: só um dos grupos vem preenchido. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Icms(
            @JsonProperty("ICMS00") Icms00 icms00,
            @JsonProperty("ICMS20") Icms20 icms20,
            @JsonProperty("ICMS40") Icms40 icms40) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Icms00(String orig, @JsonProperty("CST") String cst, String modBC, String vBC, String pICMS,
                         String vICMS) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Icms20(String orig, @JsonProperty("CST") String cst, String modBC, String pRedBC, String vBC,
                         String pICMS, String vICMS) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Icms40(String orig, @JsonProperty("CST") String cst) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Pis(
            @JsonProperty("PISAliq") PisAliq pisAliq,
            @JsonProperty("PISNT") PisNt pisNt,
            @JsonProperty("PISOutr") PisOutr pisOutr) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PisAliq(@JsonProperty("CST") String cst, String vBC, String pPIS, String vPIS) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PisNt(@JsonProperty("CST") String cst) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PisOutr(@JsonProperty("CST") String cst, String vBC, String pPIS, String vPIS) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Cofins(
            @JsonProperty("COFINSAliq") CofinsAliq cofinsAliq,
            @JsonProperty("COFINSNT") CofinsNt cofinsNt,
            @JsonProperty("COFINSOutr") CofinsOutr cofinsOutr) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CofinsAliq(@JsonProperty("CST") String cst, String vBC, String pCOFINS, String vCOFINS) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CofinsNt(@JsonProperty("CST") String cst) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CofinsOutr(@JsonProperty("CST") String cst, String vBC, String pCOFINS, String vCOFINS) {
    }

    /** Grupo UB12 (NT 2025.002): IBS/CBS do item, sem diferimento/devolução/redução nesta etapa. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IbsCbs(@JsonProperty("CST") String cst, String cClassTrib,
                         @JsonProperty("gIBSCBS") GIbsCbs gIbsCbs) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GIbsCbs(String vBC,
                          @JsonProperty("gIBSUF") GIbsUf gIbsUf,
                          @JsonProperty("gIBSMun") GIbsMun gIbsMun,
                          String vIBS,
                          @JsonProperty("gCBS") GCbs gCbs) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GIbsUf(String pIBSUF, String vIBSUF) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GIbsMun(String pIBSMun, String vIBSMun) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GCbs(String pCBS, String vCBS) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Total(
            @JsonProperty("ICMSTot") IcmsTot icmsTot,
            @JsonProperty("IBSCBSTot") IbsCbsTot ibsCbsTot) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IcmsTot(String vBC, String vICMS, String vICMSDeson, String vFCP, String vBCST, String vST,
                          String vFCPST, String vFCPSTRet, String vProd, String vFrete, String vSeg, String vDesc,
                          String vII, String vIPI, String vIPIDevol, String vPIS, String vCOFINS, String vOutro,
                          String vNF) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IbsCbsTot(String vBCIBSCBS,
                            @JsonProperty("gIBS") TotIbs gIbs,
                            @JsonProperty("gCBS") TotCbs gCbs) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TotIbs(@JsonProperty("gIBSUF") TotIbsUf gIbsUf,
                         @JsonProperty("gIBSMun") TotIbsMun gIbsMun,
                         String vIBS, String vCredPres, String vCredPresCondSus) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TotIbsUf(String vDif, String vDevTrib, String vIBSUF) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TotIbsMun(String vDif, String vDevTrib, String vIBSMun) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TotCbs(String vDif, String vDevTrib, String vCBS, String vCredPres, String vCredPresCondSus) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Transp(String modFrete) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Pag(@JsonProperty("detPag") DetPag detPag) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DetPag(String tPag, String vPag) {
    }
}
