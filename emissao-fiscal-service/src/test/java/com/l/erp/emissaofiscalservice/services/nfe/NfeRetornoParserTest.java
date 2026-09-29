package com.l.erp.emissaofiscalservice.services.nfe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Retornos reais têm namespace e vêm dentro do envelope SOAP 1.2; o parser acha o elemento pelo nome. */
class NfeRetornoParserTest {

    private static final String NS = "xmlns=\"http://www.portalfiscal.inf.br/nfe\"";
    private final NfeRetornoParser parser = new NfeRetornoParser();

    private static String soap(String corpo) {
        return "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\"><soap:Body>"
                + "<nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4\">" + corpo
                + "</nfeResultMsg></soap:Body></soap:Envelope>";
    }

    @Test
    void lerEnvioDevolveReciboQuandoLoteRecebido() {
        var r = parser.lerEnvio(soap("<retEnviNFe " + NS + " versao=\"4.00\"><tpAmb>2</tpAmb><verAplic>SVRS</verAplic>"
                + "<cStat>103</cStat><xMotivo>Lote recebido com sucesso</xMotivo><cUF>43</cUF>"
                + "<dhRecbto>2026-09-29T10:00:00-03:00</dhRecbto><infRec><nRec>431000000000001</nRec><tMed>1</tMed></infRec></retEnviNFe>"));

        assertEquals("103", r.cStat());
        assertEquals("431000000000001", r.infRec().nRec());
        assertNull(r.protNFe());
    }

    @Test
    void lerConsultaReciboDevolveProtocoloAutorizado() {
        var r = parser.lerConsultaRecibo(soap("<retConsReciNFe " + NS + " versao=\"4.00\"><tpAmb>2</tpAmb><nRec>1</nRec>"
                + "<cStat>104</cStat><xMotivo>Lote processado</xMotivo><cUF>43</cUF>"
                + "<protNFe versao=\"4.00\"><infProt Id=\"ID1\"><tpAmb>2</tpAmb><chNFe>4326</chNFe>"
                + "<dhRecbto>2026-09-29T10:00:01-03:00</dhRecbto><nProt>143000000000001</nProt><digVal>abc</digVal>"
                + "<cStat>100</cStat><xMotivo>Autorizado o uso da NF-e</xMotivo></infProt></protNFe></retConsReciNFe>"));

        assertEquals("104", r.cStat());
        assertEquals("100", r.protNFe().infProt().cStat());
        assertEquals("143000000000001", r.protNFe().infProt().nProt());
    }

    @Test
    void lerConsultaSituacaoSemProtocoloQuandoNaoConsta() {
        var r = parser.lerConsultaSituacao(soap("<retConsSitNFe " + NS + " versao=\"4.00\"><tpAmb>2</tpAmb>"
                + "<cStat>217</cStat><xMotivo>Rejeição: NF-e não consta na base de dados da SEFAZ</xMotivo><cUF>43</cUF></retConsSitNFe>"));

        assertEquals("217", r.cStat());
        assertNull(r.protNFe());
    }

    @Test
    void elementoAusenteOuXmlInvalidoViraErroDeSistema() {
        assertThrows(IllegalStateException.class, () -> parser.lerEnvio(soap("<outra/>")));
        assertThrows(IllegalStateException.class, () -> parser.lerEnvio("isto não é xml"));
    }
}
