package com.l.erp.billingservice.services;

import com.l.erp.billingservice.infra.exception.WebhookAuthException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSecurityServiceTest {

    private static final String TOKEN = "s3cr3t-webhook-token";

    @Test
    void validToken_passes() {
        WebhookSecurityService service = new WebhookSecurityService(TOKEN);

        assertThatNoException().isThrownBy(() -> service.validateToken(TOKEN));
    }

    @Test
    void invalidToken_throws() {
        WebhookSecurityService service = new WebhookSecurityService(TOKEN);

        assertThatThrownBy(() -> service.validateToken("token-errado"))
                .isInstanceOf(WebhookAuthException.class);
    }

    @Test
    void nullToken_throws() {
        WebhookSecurityService service = new WebhookSecurityService(TOKEN);

        assertThatThrownBy(() -> service.validateToken(null))
                .isInstanceOf(WebhookAuthException.class);
    }
}