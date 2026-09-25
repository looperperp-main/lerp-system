package com.l.erp.emissaofiscalservice.services.soap;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sobe um servidor HTTPS local exigindo certificado de cliente (mesma fixture do assinador) pra
 * provar o handshake TLS mútuo do cliente SOAP sem depender de rede/SEFAZ — mesmo espírito do
 * fixture do §5 da spec, aplicado ao transporte em vez do XML de resposta.
 */
class HttpWebServiceClientTest {

    private static final char[] SENHA = "teste123".toCharArray();

    private HttpsServer servidor;

    @AfterEach
    void pararServidor() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void envelopaEEnviaComTlsMutuoEDevolveARespostaDoAutorizador() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = getClass().getResourceAsStream("/certificado-teste.p12")) {
            keyStore.load(is, SENHA);
        }
        X509Certificate certificado = (X509Certificate) keyStore.getCertificate(keyStore.aliases().nextElement());

        AtomicReference<String> corpoRecebido = new AtomicReference<>();
        int porta = subirServidorDeTeste(keyStore, certificado, corpoRecebido);
        TrustManager[] confiaNoServidorDeTeste = confiar(certificado);

        HttpWebServiceClient client = new HttpWebServiceClient();
        String resposta = client.enviar(
                URI.create("https://localhost:" + porta + "/ws"),
                "http://sefaz/nfeAutorizacao",
                "<nfeDadosMsg xmlns=\"http://teste\"><chave>123</chave></nfeDadosMsg>",
                keyStore, SENHA, confiaNoServidorDeTeste);

        assertTrue(resposta.contains("resultadoAutorizado"), "deveria devolver o corpo da resposta do autorizador");
        assertTrue(corpoRecebido.get().contains("<soap12:Envelope"), "deveria ter envelopado em SOAP 1.2");
        assertTrue(corpoRecebido.get().contains("<nfeDadosMsg"), "deveria ter preservado o corpo específico do serviço");
    }

    private int subirServidorDeTeste(KeyStore keyStore, X509Certificate certificadoCliente,
                                      AtomicReference<String> corpoRecebido) throws Exception {
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, SENHA);

        KeyStore trustStoreServidor = KeyStore.getInstance("PKCS12");
        trustStoreServidor.load(null, null);
        trustStoreServidor.setCertificateEntry("cliente-teste", certificadoCliente);
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStoreServidor);

        SSLContext sslContextServidor = SSLContext.getInstance("TLS");
        sslContextServidor.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);

        servidor = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.setHttpsConfigurator(new HttpsConfigurator(sslContextServidor) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters sslParameters = sslContextServidor.getDefaultSSLParameters();
                sslParameters.setNeedClientAuth(true);
                params.setSSLParameters(sslParameters);
            }
        });
        servidor.createContext("/ws", exchange -> {
            corpoRecebido.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resposta = ("<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                    + "<soap12:Body><resultadoAutorizado>OK</resultadoAutorizado></soap12:Body></soap12:Envelope>")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/soap+xml; charset=utf-8");
            exchange.sendResponseHeaders(200, resposta.length);
            exchange.getResponseBody().write(resposta);
            exchange.close();
        });
        servidor.start();
        return servidor.getAddress().getPort();
    }

    private TrustManager[] confiar(X509Certificate certificado) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("servidor-teste", certificado);
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        return trustManagerFactory.getTrustManagers();
    }
}
