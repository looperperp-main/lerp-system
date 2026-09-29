package com.l.erp.emissaofiscalservice.services.nfe;

import com.l.erp.common.util.Constants;

/**
 * Corpos SOAP dos três serviços usados na transmissão (MOC 7.0): {@code enviNFe} (lote),
 * {@code consReciNFe} (resultado do lote) e {@code consSitNFe} (situação por chave). O envelope
 * SOAP 1.2 é montado pelo {@code WebServiceClient}; aqui só o {@code nfeDadosMsg}.
 */
public final class NfeMensagens {

    private static final String NS_NFE = "http://www.portalfiscal.inf.br/nfe";
    private static final String NS_WSDL = "http://www.portalfiscal.inf.br/nfe/wsdl/";

    /** ponytail: {@code indSinc=0} (assíncrono) — sincronia com 1 NF-e não confirmada na SVRS. */
    private static final String IND_SINC_ASSINCRONO = "0";

    public record Mensagem(String soapAction, String corpo) {}

    private NfeMensagens() {
    }

    public static Mensagem enviNFe(long idLote, String nfeAssinada) {
        String conteudo = "<enviNFe xmlns=\"" + NS_NFE + "\" versao=\"" + Constants.NFE_VERSAO_LEIAUTE + "\">"
                + "<idLote>" + idLote + "</idLote><indSinc>" + IND_SINC_ASSINCRONO + "</indSinc>"
                + nfeAssinada + "</enviNFe>";
        return montar("NFeAutorizacao4", "nfeAutorizacaoLote", conteudo);
    }

    public static Mensagem consReciNFe(String tpAmb, String recibo) {
        String conteudo = "<consReciNFe xmlns=\"" + NS_NFE + "\" versao=\"" + Constants.NFE_VERSAO_LEIAUTE + "\">"
                + "<tpAmb>" + tpAmb + "</tpAmb><nRec>" + recibo + "</nRec></consReciNFe>";
        return montar("NFeRetAutorizacao4", "nfeRetAutorizacaoLote", conteudo);
    }

    public static Mensagem consSitNFe(String tpAmb, String chave) {
        String conteudo = "<consSitNFe xmlns=\"" + NS_NFE + "\" versao=\"" + Constants.NFE_VERSAO_LEIAUTE + "\">"
                + "<tpAmb>" + tpAmb + "</tpAmb><xServ>CONSULTAR</xServ><chNFe>" + chave + "</chNFe></consSitNFe>";
        return montar("NFeConsultaProtocolo4", "nfeConsultaNF", conteudo);
    }

    /** {@code tpAmb} da NF-e: 1 produção, 2 homologação. */
    public static String tpAmb(String ambiente) {
        return "HOMOLOGACAO".equalsIgnoreCase(ambiente) ? "2" : "1";
    }

    private static Mensagem montar(String servico, String operacao, String conteudo) {
        String ns = NS_WSDL + servico;
        return new Mensagem(ns + "/" + operacao, "<nfeDadosMsg xmlns=\"" + ns + "\">" + conteudo + "</nfeDadosMsg>");
    }
}
