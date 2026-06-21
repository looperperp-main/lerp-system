package com.l.erp.billingservice.services;

import com.l.erp.billingservice.infra.exception.WebhookAuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Valida o token enviado pelo Asaas no header {@code asaas-access-token} (spec §5.1).
 *
 * <p>Usa {@link MessageDigest#isEqual} (comparação em tempo constante) para evitar
 * timing attack. O token esperado é configurado em {@code asaas.webhook-token}.</p>
 */
@Service
public class WebhookSecurityService {

    private final byte[] expectedToken;

    public WebhookSecurityService(@Value("${asaas.webhook-token}") String expectedToken) {
        this.expectedToken = expectedToken == null
                ? new byte[0]
                : expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @param receivedToken token recebido no header do webhook
     * @throws WebhookAuthException se o token for nulo ou divergente
     */
    public void validateToken(String receivedToken) {
        byte[] received = receivedToken == null
                ? new byte[0]
                : receivedToken.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expectedToken, received)) {
            throw new WebhookAuthException("Token de webhook inválido");
        }
    }
}