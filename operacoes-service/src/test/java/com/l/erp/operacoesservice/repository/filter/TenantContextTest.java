package com.l.erp.operacoesservice.repository.filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @AfterEach
    void limpa() {
        TenantContext.clear();
    }

    @Test
    void deveArmazenarERecuperarTenantIdNaThreadAtual() {
        TenantContext.setTenantId(7L);

        assertThat(TenantContext.getTenantId()).isEqualTo(7L);
    }

    @Test
    void deveRetornarNuloAntesDeQualquerSet() {
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void clearDeveLimparOTenantIdArmazenado() {
        TenantContext.setTenantId(7L);

        TenantContext.clear();

        assertThat(TenantContext.getTenantId()).isNull();
    }
}
