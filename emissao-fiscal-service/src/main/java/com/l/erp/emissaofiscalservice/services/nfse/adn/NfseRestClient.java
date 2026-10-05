package com.l.erp.emissaofiscalservice.services.nfse.adn;

import java.net.URI;
import java.security.KeyStore;

/**
 * Transporte REST/JSON do ADN com TLS mútuo (o A1 do emitente autentica — não há token). Interface
 * porque os testes de build servem respostas gravadas sem rede, como no {@code WebServiceClient} (spec §5).
 */
public interface NfseRestClient {

    /** Falha de rede (timeout, conexão) sai como {@code WebServiceComunicacaoException}; qualquer HTTP volta em {@link Resposta}. */
    Resposta enviar(String metodo, URI uri, String corpoJson, KeyStore keyStoreCliente, char[] senhaKeyStore);

    record Resposta(int status, String corpo) {
    }
}
