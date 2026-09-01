package com.l.erp.billingservice.infra.config;

import com.l.erp.common.util.Constants;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsConfigTest {

    @Test
    void comHeaderTenant_extraiValor() {
        var req = new MockHttpServletRequest();
        req.addHeader(Constants.HEADER_TENANT_ID, "42");

        assertThat(MetricsConfig.extractTenantId(req)).isEqualTo("42");
    }

    @Test
    void semHeaderTenant_retornaNull() {
        var req = new MockHttpServletRequest();

        assertThat(MetricsConfig.extractTenantId(req)).isNull();
    }

    @Test
    void headerTenantEmBranco_retornaNull() {
        var req = new MockHttpServletRequest();
        req.addHeader(Constants.HEADER_TENANT_ID, "   ");

        assertThat(MetricsConfig.extractTenantId(req)).isNull();
    }
}
