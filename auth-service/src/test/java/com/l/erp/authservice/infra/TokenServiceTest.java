package com.l.erp.authservice.infra;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Cobre o check de tamanho mínimo do JWT_SECRET no startup (@PostConstruct), reativado após
 * ficar comentado em produção — spec/auditoria.md §2.2.
 */
class TokenServiceTest {

    private TokenService serviceWithSecret(String secret) {
        TokenService service = new TokenService();
        ReflectionTestUtils.setField(service, "secret", secret);
        return service;
    }

    @Test
    void secretNulo_falhaNoStartup() {
        TokenService service = serviceWithSecret(null);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "validateSecret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 caracteres");
    }

    @Test
    void secretCurto_falhaNoStartup() {
        TokenService service = serviceWithSecret("secret-curto-31-caracteres-xxx");

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "validateSecret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 caracteres");
    }

    @Test
    void secretComExatos32Caracteres_passa() {
        TokenService service = serviceWithSecret("a".repeat(32));

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(service, "validateSecret"))
                .doesNotThrowAnyException();
    }

    @Test
    void secretLongo_passa() {
        TokenService service = serviceWithSecret("a".repeat(64));

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(service, "validateSecret"))
                .doesNotThrowAnyException();
    }
}