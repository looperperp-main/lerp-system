package com.l.erp.emissaofiscalservice.services.nfe;

import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.api.dto.DestinatarioDTO;
import com.l.erp.emissaofiscalservice.api.dto.DocumentoFiscalRequestDTO;
import com.l.erp.emissaofiscalservice.api.dto.EmitenteDTO;
import com.l.erp.emissaofiscalservice.api.dto.EnderecoDTO;
import com.l.erp.emissaofiscalservice.api.dto.ItemDocumentoDTO;
import com.l.erp.emissaofiscalservice.api.dto.SnapshotFiscalItemDTO;
import com.l.erp.emissaofiscalservice.services.assinatura.XmlSignatureService;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.SchemaFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rede de segurança da Fatia 2: o XML montado (e assinado, porque o XSD exige {@code <Signature>}) tem
 * que passar no XSD oficial do PL 010f. Pega ordem de elementos, elemento faltando e violação de
 * facet (tamanho, casas decimais) — sem tocar a SEFAZ (spec §3 item 2 e §5, estratégia de CI).
 */
class NfeXmlBuilderTest {

    private static final char[] SENHA = "teste123".toCharArray();
    private static final String CNPJ = "11222333000181";
    private static final BigDecimal CEM = new BigDecimal("100.00");

    private final NfeXmlBuilder builder = new NfeXmlBuilder();

    @Test
    void xmlAssinadoValidaContraOXsdDoPl010f() throws Exception {
        Document assinado = montarEAssinar(request("HOMOLOGACAO", "00"));

        assertDoesNotThrow(() -> validarContraXsd(assinado));
    }

    @Test
    void cobreOsTresGruposDeIcmsSuportados() throws Exception {
        for (String cst : List.of("00", "20", "40")) {
            Document assinado = montarEAssinar(request("HOMOLOGACAO", cst));
            assertDoesNotThrow(() -> validarContraXsd(assinado), "CST ICMS " + cst + " deveria validar no XSD");
        }
    }

    @Test
    void cnpjDoDestinatarioAlfanumericoValidaNoXsd() throws Exception {
        DocumentoFiscalRequestDTO base = request("HOMOLOGACAO", "00");
        DestinatarioDTO alfa = new DestinatarioDTO("12ABC34501DE35", "Cliente Alfa", "1", "123456789", null,
                base.destinatario().endereco());
        DocumentoFiscalRequestDTO r = comDestinatario(base, alfa);

        Document assinado = montarEAssinar(r);

        assertDoesNotThrow(() -> validarContraXsd(assinado));
        assertTrue(serializar(assinado).contains("<CNPJ>12ABC34501DE35</CNPJ>"));
    }

    @Test
    void idDaInfNFeEhNFeMaisAChaveEHomologacaoTrocaOsTextos() {
        DocumentoFiscalRequestDTO r = request("HOMOLOGACAO", "00");
        ChaveAcesso.Resultado chave = chave();
        String xml = builder.montar(r, 123L, chave, emissao());

        assertTrue(xml.startsWith("<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"><infNFe"));
        assertTrue(xml.contains("Id=\"NFe" + chave.chave() + "\""));
        assertTrue(xml.contains("<tpAmb>2</tpAmb>"));
        assertTrue(xml.contains("<xNome>" + Constants.NFE_TEXTO_HOMOLOGACAO_DESTINATARIO + "</xNome>"));
        assertTrue(xml.contains("<xProd>" + Constants.NFE_TEXTO_HOMOLOGACAO_PRODUTO + "</xProd>"));
        assertFalse(xml.contains("Cliente Teste"), "em homologação o nome real do destinatário não vai no XML");
    }

    @Test
    void producaoMantemOsDadosReais() {
        DocumentoFiscalRequestDTO r = request("PRODUCAO", "00");
        String xml = builder.montar(r, 123L, chave(), emissao());

        assertTrue(xml.contains("<tpAmb>1</tpAmb>"));
        assertTrue(xml.contains("<xNome>Cliente Teste</xNome>"));
        assertTrue(xml.contains("<xProd>Produto teste</xProd>"));
    }

    @Test
    void operacaoInterestadualMarcaIdDest2() {
        DocumentoFiscalRequestDTO base = request("HOMOLOGACAO", "00");
        EnderecoDTO sp = new EnderecoDTO("Rua B", "20", null, "Centro", "3550308", "Sao Paulo", "SP", "01001000", null);
        DocumentoFiscalRequestDTO r = comDestinatario(base,
                new DestinatarioDTO("12345678909", "Cliente SP", "1", "123456789", null, sp));

        assertTrue(builder.montar(r, 1L, chave(), emissao()).contains("<idDest>2</idDest>"));
    }

    @Test
    void formatoDosNumerosSegueAsCasasDecimaisDoLeiaute() {
        assertEquals("100.00", NfeXmlBuilder.dinheiro(new BigDecimal("100")));
        assertEquals("0.13", NfeXmlBuilder.dinheiro(new BigDecimal("0.125")));
        assertEquals("0.00", NfeXmlBuilder.dinheiro(null));
        assertEquals("18.00", NfeXmlBuilder.percentual(new BigDecimal("18")));
        assertEquals("1.65", NfeXmlBuilder.percentual(new BigDecimal("1.6500")));
        assertEquals("0.1235", NfeXmlBuilder.percentual(new BigDecimal("0.12345")));
        assertEquals("2", NfeXmlBuilder.quantidade(new BigDecimal("2.0000")));
        assertEquals("1.5", NfeXmlBuilder.quantidade(new BigDecimal("1.50")));
        assertEquals("10", NfeXmlBuilder.valorUnitario(new BigDecimal("10.00")));
        assertEquals("0.0000000001", NfeXmlBuilder.valorUnitario(new BigDecimal("0.0000000001")));
    }

    // ---- helpers ----

    private Document montarEAssinar(DocumentoFiscalRequestDTO r) throws Exception {
        ChaveAcesso.Resultado chave = chave();
        String xml = builder.montar(r, 123L, chave, emissao());

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document documento = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = getClass().getResourceAsStream("/certificado-teste.p12")) {
            keyStore.load(is, SENHA);
        }
        String alias = keyStore.aliases().nextElement();
        PrivateKey chavePrivada = (PrivateKey) keyStore.getKey(alias, SENHA);
        X509Certificate certificado = (X509Certificate) keyStore.getCertificate(alias);

        return new XmlSignatureService().assinar(documento, "NFe" + chave.chave(), chavePrivada, certificado);
    }

    private void validarContraXsd(Document documento) throws Exception {
        var schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(getClass().getResource("/xsd/nfe/nfe_v4.00.xsd"));
        schema.newValidator().validate(new DOMSource(documento));
    }

    private String serializar(Document documento) throws Exception {
        StringWriter out = new StringWriter();
        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(documento), new StreamResult(out));
        return out.toString();
    }

    private static ChaveAcesso.Resultado chave() {
        return ChaveAcesso.gerar("RJ", YearMonth.of(2026, 9), CNPJ, "55", "1", 123L, "1", "45678901");
    }

    private static OffsetDateTime emissao() {
        return OffsetDateTime.of(2026, 9, 30, 10, 15, 30, 0, ZoneOffset.of("-03:00"));
    }

    private static EnderecoDTO enderecoRj() {
        return new EnderecoDTO("Rua A", "10", null, "Centro", "3304557", "Rio de Janeiro", "RJ", "20040020", null);
    }

    private static DocumentoFiscalRequestDTO request(String ambiente, String cstIcms) {
        boolean tributado = "00".equals(cstIcms) || "20".equals(cstIcms);
        SnapshotFiscalItemDTO fiscal = new SnapshotFiscalItemDTO("5102", cstIcms, "000", "000001",
                tributado ? CEM : null, tributado ? new BigDecimal("18.00") : null,
                tributado ? new BigDecimal("18.00") : null,
                "20".equals(cstIcms) ? new BigDecimal("33.33") : null, null,
                CEM, new BigDecimal("0.10"), new BigDecimal("0.05"), new BigDecimal("0.90"), null,
                new BigDecimal("0.10"), new BigDecimal("0.05"), new BigDecimal("0.90"),
                "01", CEM, new BigDecimal("1.65"), new BigDecimal("1.65"),
                "01", CEM, new BigDecimal("7.60"), new BigDecimal("7.60"),
                null, null, null, null, null, null);
        ItemDocumentoDTO item = new ItemDocumentoDTO("P1", "Produto teste", "12345678", "0", "UN",
                new BigDecimal("1.0000"), CEM, CEM, fiscal);
        return new DocumentoFiscalRequestDTO(UUID.randomUUID(), "NFE", "55", "1", ambiente, "Venda de mercadoria",
                1, 0, 1,
                new EmitenteDTO(CNPJ, "Empresa Teste Ltda", null, "123456789", "3", enderecoRj()),
                new DestinatarioDTO("12345678909", "Cliente Teste", "1", "123456789", "cliente@teste.com", enderecoRj()),
                CEM, List.of(item));
    }

    private static DocumentoFiscalRequestDTO comDestinatario(DocumentoFiscalRequestDTO r, DestinatarioDTO d) {
        return new DocumentoFiscalRequestDTO(r.emitenteId(), r.documento(), r.modelo(), r.serie(), r.ambiente(),
                r.naturezaOperacao(), r.tipoOperacao(), r.indicadorFinal(), r.indicadorPresenca(),
                r.emitente(), d, r.valorTotal(), r.itens());
    }
}
