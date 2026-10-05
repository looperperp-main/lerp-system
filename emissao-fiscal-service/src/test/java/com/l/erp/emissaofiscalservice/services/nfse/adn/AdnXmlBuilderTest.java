package com.l.erp.emissaofiscalservice.services.nfse.adn;

import com.l.erp.emissaofiscalservice.api.dto.EnderecoDTO;
import com.l.erp.emissaofiscalservice.api.dto.NfseRequestDTO;
import com.l.erp.emissaofiscalservice.services.assinatura.XmlSignatureService;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.SchemaFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O XML montado e <b>assinado</b> passa no XSD oficial 1.00 do Sistema Nacional NFS-e e a assinatura
 * valida depois de serializar — a rede de segurança contra ordem/facet errados (a mesma estratégia do
 * {@code NfeXmlBuilderTest}).
 */
class AdnXmlBuilderTest {

    private static final char[] SENHA = "teste123".toCharArray();
    private static final String MUNICIPIO = "3104007";
    private static final String CNPJ = "40152424000196";
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 10, 3, 10, 30, 0, 0, ZoneOffset.ofHours(-3));
    private static final String CHAVE = "31040072240152424000196000000000000126100123456789";

    private final AdnXmlBuilder builder = new AdnXmlBuilder();
    private final XmlSignatureService assinatura = new XmlSignatureService();

    @Test
    void dpsAssinadaValidaNoXsdEAssinaturaConfere() throws Exception {
        String dps = builder.montarDps(pedido(true), MUNICIPIO, 7, AGORA);
        Document assinado = assinar(dps, AdnXmlBuilder.idDps(MUNICIPIO, CNPJ, "1", 7));

        validarContraXsd("/xsd/nfse/DPS_v1.00.xsd", assinado);
        assertTrue(assinaturaValida(assinado, "infDPS"));
    }

    @Test
    void dpsSemTomadorComplementoEAliquotaTambemValida() throws Exception {
        String dps = builder.montarDps(pedido(false), MUNICIPIO, 1, AGORA);
        Document assinado = assinar(dps, AdnXmlBuilder.idDps(MUNICIPIO, CNPJ, "1", 1));

        validarContraXsd("/xsd/nfse/DPS_v1.00.xsd", assinado);
        assertFalse(dps.contains("<toma>"));
        assertFalse(dps.contains("pAliq"));
    }

    @Test
    void idDpsTem45PosicoesNaOrdemDoLeiaute() {
        String id = AdnXmlBuilder.idDps(MUNICIPIO, CNPJ, "1", 7);

        assertEquals(45, id.length());
        assertEquals("DPS" + MUNICIPIO + "2" + CNPJ + "00001" + "000000000000007", id);
    }

    @Test
    void cancelamentoAssinadoValidaNoXsdDoPedidoDeEvento() throws Exception {
        String pedido = builder.montarCancelamento("HOMOLOGACAO", CNPJ, CHAVE,
                "Erro na emissao: valor do servico digitado errado", AGORA);
        Document assinado = assinar(pedido, AdnXmlBuilder.idPedidoCancelamento(CHAVE));

        validarContraXsd("/xsd/nfse/pedRegEvento_v1.00.xsd", assinado);
        assertTrue(assinaturaValida(assinado, "infPedReg"));
        assertEquals(62, AdnXmlBuilder.idPedidoCancelamento(CHAVE).length());
    }

    @Test
    void ambienteProducaoVira1EQualquerOutroVira2() {
        assertEquals("1", AdnXmlBuilder.tpAmb("PRODUCAO"));
        assertEquals("2", AdnXmlBuilder.tpAmb("HOMOLOGACAO"));
    }

    // ---- apoio ----

    private KeyStore keyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = getClass().getResourceAsStream("/certificado-teste.p12")) {
            keyStore.load(is, SENHA);
        }
        return keyStore;
    }

    private Document assinar(String xml, String idAssinado) throws Exception {
        return assinatura.assinar(XmlSignatureService.parse(xml), idAssinado, keyStore(), SENHA);
    }

    private void validarContraXsd(String caminho, Document documento) throws Exception {
        var schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(getClass().getResource(caminho));
        assertDoesNotThrow(() -> schema.newValidator().validate(new DOMSource(documento)));
    }

    /** Reserializa e relê antes de validar: o que vale é a assinatura sobreviver ao texto que vai no JSON. */
    private boolean assinaturaValida(Document documento, String elementoAssinado) throws Exception {
        Document relido = XmlSignatureService.parse(XmlSignatureService.serializar(documento));
        var no = relido.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").item(0);
        X509Certificate certificado = (X509Certificate) keyStore().getCertificate(keyStore().aliases().nextElement());
        DOMValidateContext contexto = new DOMValidateContext(certificado.getPublicKey(), no);
        contexto.setIdAttributeNS((Element) relido.getElementsByTagName(elementoAssinado).item(0), null, "Id");
        // o leiaute exige RSA-SHA1; o JDK recusa SHA-1 com secureValidation ligada (só afeta a validação do teste)
        contexto.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);
        return XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(contexto).validate(contexto);
    }

    private static NfseRequestDTO pedido(boolean completo) {
        EnderecoDTO endereco = new EnderecoDTO("Rua das Flores", "100", completo ? "Sala 2" : null, "Centro",
                MUNICIPIO, "Arcos", "MG", "35588000", completo ? "37999990000" : null);
        var prestador = new NfseRequestDTO.Prestador(CNPJ, completo ? "12345" : null, "ARCOS TECNOLOGIA LTDA",
                endereco, "contato@exemplo.com.br", "3", "1", "0");
        var tomador = completo
                ? new NfseRequestDTO.Tomador("11222333000181", "CLIENTE EXEMPLO LTDA", "cliente@exemplo.com.br", endereco)
                : null;
        var servico = new NfseRequestDTO.Servico("010101", completo ? "001" : null,
                "Desenvolvimento de software sob encomenda", MUNICIPIO);
        var valores = new NfseRequestDTO.Valores(new BigDecimal("1500.5"), "1",
                completo ? new BigDecimal("2") : null, "1");
        return new NfseRequestDTO(UUID.randomUUID(), "HOMOLOGACAO", "1", LocalDate.of(2026, 10, 3),
                prestador, tomador, servico, valores);
    }
}
