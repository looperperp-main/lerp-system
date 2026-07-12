package com.l.erp.common.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class NoHtmlValidatorTest {

    private final NoHtmlValidator validator = new NoHtmlValidator();

    @Test
    void nullIsValid() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"BRINK'S TRANSPORTE", "Johnson & Johnson", "Ltda.", "Fornecedor 123", ""})
    void plainTextIsValid(String value) {
        assertThat(validator.isValid(value, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"<script>alert(1)</script>", "<img src=x onerror=alert(1)>", "</div>", "<!--comment-->"})
    void htmlIsInvalid(String value) {
        assertThat(validator.isValid(value, null)).isFalse();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void doesNotHangOnAdversarialWhitespaceInput() {
        // ponytail: regression guard for the polynomial-backtracking regex bug (fixed via
        // possessive quantifiers). Without the fix this input times out well before 2s.
        String malicious = "<" + " ".repeat(50_000);
        assertThat(validator.isValid(malicious, null)).isTrue();
    }
}
