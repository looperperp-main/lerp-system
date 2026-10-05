package com.l.erp.emissaofiscalservice.services.nfse.adn;

import com.l.erp.emissaofiscalservice.services.soap.HttpWebServiceClient;
import com.l.erp.emissaofiscalservice.services.soap.WebServiceComunicacaoException;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;

/** Mesmo TLS mútuo do cliente SOAP; só {@code java.net.http} (JDK), nenhuma lib HTTP nova. */
@Service
public class HttpNfseRestClient implements NfseRestClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Override
    public Resposta enviar(String metodo, URI uri, String corpoJson, KeyStore keyStoreCliente, char[] senhaKeyStore) {
        try {
            SSLContext sslContext = HttpWebServiceClient.montarSslContext(keyStoreCliente, senhaKeyStore, null);
            HttpClient httpClient = HttpClient.newBuilder().sslContext(sslContext).connectTimeout(TIMEOUT).build();
            HttpRequest.BodyPublisher corpo = corpoJson == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(corpoJson, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .method(metodo, corpo)
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Resposta(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new WebServiceComunicacaoException("Falha de comunicação com " + uri + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebServiceComunicacaoException("Comunicação com " + uri + " interrompida.", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao montar TLS mútuo com o certificado do emitente.", e);
        }
    }
}
