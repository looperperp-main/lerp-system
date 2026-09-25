package com.l.erp.emissaofiscalservice.services.soap;

import java.net.URI;
import java.security.KeyStore;

/**
 * Cliente SOAP genérico pros webservices de autorização (NF-e/NFC-e/CT-e) — spec §3 item 4:
 * "um cliente parametrizado por UF/endpoint, não um por UF". Quem chama monta o corpo específico
 * do serviço (ex. {@code nfeDadosMsg}) — este cliente só envelopa, autentica via TLS mútuo com o
 * certificado do tenant e transmite.
 *
 * <p>Interface existe porque a spec (§5, estratégia de CI) já prevê uma segunda implementação —
 * um stub servindo fixture gravada, sem rede, pros testes de build — não é abstração especulativa.</p>
 */
public interface WebServiceClient {

    String enviar(URI endpoint, String soapAction, String corpoServico, KeyStore keyStoreCliente, char[] senhaKeyStore);
}
