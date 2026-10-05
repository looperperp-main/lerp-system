package com.l.erp.emissaofiscalservice.services.assinatura;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.List;

/**
 * Assinatura XMLDSig padrão SEFAZ/ENCAT (assinatura envelopada, enveloped-signature + C14N,
 * RSA-SHA1/SHA1 — exigido pelo manual de integração do contribuinte, não escolha deste serviço) —
 * spec/modulos/emissao-fiscal/emissao-fiscal.md §3 item 2.
 *
 * <p>Usa só {@code javax.xml.crypto.dsig} (JDK stdlib, módulo {@code java.xml.crypto}) — nenhuma
 * biblioteca de terceiros para a assinatura em si. Genérico por documento: recebe o elemento a
 * assinar pelo atributo {@code Id}, igual ao padrão NFe ({@code infNFe}), CT-e ({@code infCte}) etc.,
 * onde a assinatura é anexada como irmão desse elemento, dentro do elemento raiz.</p>
 */
@Service
public class XmlSignatureService {

    public Document assinar(Document xmlEntrada, String idElementoAssinado, PrivateKey chavePrivada,
                             X509Certificate certificado) {
        try {
            Element elementoAssinado = buscarElementoPorId(xmlEntrada, idElementoAssinado);

            XMLSignatureFactory fabrica = XMLSignatureFactory.getInstance("DOM");

            Reference referencia = fabrica.newReference(
                    "#" + idElementoAssinado,
                    fabrica.newDigestMethod(DigestMethod.SHA1, null),
                    List.of(
                            fabrica.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
                            fabrica.newTransform(CanonicalizationMethod.INCLUSIVE, (TransformParameterSpec) null)),
                    null, null);

            SignedInfo signedInfo = fabrica.newSignedInfo(
                    fabrica.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                    fabrica.newSignatureMethod(SignatureMethod.RSA_SHA1, null),
                    List.of(referencia));

            KeyInfoFactory keyInfoFactory = fabrica.getKeyInfoFactory();
            X509Data x509Data = keyInfoFactory.newX509Data(List.of(certificado));
            KeyInfo keyInfo = keyInfoFactory.newKeyInfo(List.of(x509Data));

            DOMSignContext contextoAssinatura = new DOMSignContext(chavePrivada, xmlEntrada.getDocumentElement());
            contextoAssinatura.setIdAttributeNS(elementoAssinado, null, "Id");

            XMLSignature assinatura = fabrica.newXMLSignature(signedInfo, keyInfo);
            assinatura.sign(contextoAssinatura);

            return xmlEntrada;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao assinar XML — " + e.getMessage(), e);
        }
    }

    /** Assina com a primeira chave privada do A1 aberto — o par chave/certificado do emitente. */
    public Document assinar(Document xmlEntrada, String idElementoAssinado, KeyStore keyStore, char[] senha) {
        try {
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (keyStore.isKeyEntry(alias)) {
                    return assinar(xmlEntrada, idElementoAssinado, (PrivateKey) keyStore.getKey(alias, senha),
                            (X509Certificate) keyStore.getCertificate(alias));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler a chave privada do certificado — " + e.getMessage(), e);
        }
        throw new IllegalStateException("O certificado A1 não contém chave privada.");
    }

    /** XML → DOM com namespace e sem DTD (XXE). */
    public static Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new IllegalStateException("XML inválido — " + e.getMessage(), e);
        }
    }

    /** Sem declaração XML e sem indentação: a assinatura depende do texto exato. */
    public static String serializar(Document documento) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter saida = new StringWriter();
            transformer.transform(new DOMSource(documento), new StreamResult(saida));
            return saida.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar o XML — " + e.getMessage(), e);
        }
    }

    /** DOM puro não conhece atributos {@code ID} de DTD/schema — precisa localizar o elemento na mão. */
    static Element buscarElementoPorId(Document documento, String id) {
        NodeList todos = documento.getElementsByTagName("*");
        for (int i = 0; i < todos.getLength(); i++) {
            Node node = todos.item(i);
            if (node instanceof Element elemento && id.equals(elemento.getAttribute("Id"))) {
                return elemento;
            }
        }
        throw new IllegalArgumentException("Nenhum elemento com Id=\"" + id + "\" encontrado no XML a assinar.");
    }
}
