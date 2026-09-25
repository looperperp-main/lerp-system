package com.l.erp.emissaofiscalservice.services.assinatura;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roda ida-e-volta (assina com a chave privada, valida com a chave pública do mesmo certificado)
 * sem depender de rede/SEFAZ — a fixture {@code certificado-teste.p12} é um certificado
 * autoassinado gerado uma única vez via keytool, só para exercitar a mecânica de assinatura
 * envelopada (spec §3 item 2), não a cadeia de confiança ICP-Brasil.
 */
class XmlSignatureServiceTest {

    private static final char[] SENHA = "teste123".toCharArray();

    private final XmlSignatureService service = new XmlSignatureService();

    @Test
    void assinaXmlEValidaComAChavePublicaDoMesmoCertificado() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = getClass().getResourceAsStream("/certificado-teste.p12")) {
            keyStore.load(is, SENHA);
        }
        Enumeration<String> aliases = keyStore.aliases();
        String alias = aliases.nextElement();
        PrivateKey chavePrivada = (PrivateKey) keyStore.getKey(alias, SENHA);
        X509Certificate certificado = (X509Certificate) keyStore.getCertificate(alias);

        Document documento = documentoDeTeste();

        Document assinado = service.assinar(documento, "doc123", chavePrivada, certificado);

        var signatureNodes = assinado.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        assertEquals(1, signatureNodes.getLength(), "assinatura deveria ter sido anexada ao XML");

        Element elementoAssinado = XmlSignatureService.buscarElementoPorId(assinado, "doc123");
        DOMValidateContext contextoValidacao = new DOMValidateContext(certificado.getPublicKey(), signatureNodes.item(0));
        contextoValidacao.setIdAttributeNS(elementoAssinado, null, "Id");

        XMLSignature signature = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(contextoValidacao);
        assertTrue(signature.validate(contextoValidacao), "assinatura deveria validar contra a chave pública do certificado");
    }

    private Document documentoDeTeste() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        Document documento = factory.newDocumentBuilder().newDocument();

        Element raiz = documento.createElement("docFiscal");
        documento.appendChild(raiz);

        Element elementoAssinado = documento.createElement("infDoc");
        elementoAssinado.setAttribute("Id", "doc123");
        Element valor = documento.createElement("valorTotal");
        valor.setTextContent("199.90");
        elementoAssinado.appendChild(valor);

        raiz.appendChild(elementoAssinado);
        return documento;
    }
}
