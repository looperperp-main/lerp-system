package com.l.erp.emissaofiscalservice.services.soap;

import org.springframework.stereotype.Service;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;

/**
 * Transporte SOAP 1.2 com TLS mútuo (o certificado do tenant autentica no autorizador — não é
 * header/token) — spec §3 item 4. Usa só {@code java.net.http.HttpClient} (JDK stdlib), nenhuma
 * lib SOAP/HTTP nova.
 *
 * <p>Quando {@code trustManagers} é nulo (caminho de produção), o {@link SSLContext} usa o
 * truststore padrão da JVM — depende da cadeia ICP-Brasil estar instalada nela (spec §7.4, já
 * documentado como pré-requisito operacional, não bug deste cliente).</p>
 */
@Service
public class HttpWebServiceClient implements WebServiceClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Override
    public String enviar(URI endpoint, String soapAction, String corpoServico, KeyStore keyStoreCliente,
                          char[] senhaKeyStore) {
        return enviar(endpoint, soapAction, corpoServico, keyStoreCliente, senhaKeyStore, null);
    }

    /**
     * Overload package-private só pra teste: injeta {@code trustManagers} pra validar o handshake
     * contra um servidor HTTPS local com certificado autoassinado, sem tocar no truststore padrão
     * da JVM que o caminho de produção usa.
     */
    String enviar(URI endpoint, String soapAction, String corpoServico, KeyStore keyStoreCliente,
                  char[] senhaKeyStore, TrustManager[] trustManagers) {
        String envelope = envelopar(corpoServico);
        try {
            SSLContext sslContext = montarSslContext(keyStoreCliente, senhaKeyStore, trustManagers);
            HttpClient httpClient = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(TIMEOUT)
                    .build();

            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/soap+xml; charset=utf-8; action=\"" + soapAction + "\"")
                    .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new WebServiceComunicacaoException(
                        "Autorizador respondeu HTTP " + response.statusCode() + " em " + endpoint);
            }
            return response.body();
        } catch (IOException e) {
            throw new WebServiceComunicacaoException("Falha de comunicação com " + endpoint + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebServiceComunicacaoException("Comunicação com " + endpoint + " interrompida.", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao montar TLS mútuo com o certificado do tenant.", e);
        }
    }

    private SSLContext montarSslContext(KeyStore keyStoreCliente, char[] senha, TrustManager[] trustManagers)
            throws GeneralSecurityException {
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStoreCliente, senha);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagers, null);
        return sslContext;
    }

    static String envelopar(String corpoServico) {
        return "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap12:Body>" + corpoServico + "</soap12:Body>"
                + "</soap12:Envelope>";
    }
}
