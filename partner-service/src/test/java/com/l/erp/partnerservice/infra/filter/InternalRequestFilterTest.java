package com.l.erp.partnerservice.infra.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class InternalRequestFilterTest {

    private final InternalRequestFilter filter = new InternalRequestFilter();

    private static class CountingChain implements FilterChain {
        int calls;
        @Override public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            calls++;
        }
    }

    @Test
    void semHeaderXUserId_emPathProtegido_retorna401() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/v1/partners/dashboard");
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("Unauthorized");
        assertThat(chain.calls).isZero();
    }

    @Test
    void comHeaderXUserId_encaminha() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/v1/partners/dashboard");
        req.addHeader("X-User-Id", "user-1");
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.calls).isEqualTo(1);
    }

    @Test
    void pathDoSwagger_passaSemHeader() throws Exception {
        var req = new MockHttpServletRequest("GET", "/v3/api-docs");
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.calls).isEqualTo(1);
    }

    @Test
    void actuatorHealth_passaSemHeader() throws Exception {
        var req = new MockHttpServletRequest("GET", "/actuator/health");
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.calls).isEqualTo(1);
    }

    @Test
    void consultaCnpjPublica_passaSemHeader() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/v1/partners/cnpj/12345678000199");
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.calls).isEqualTo(1);
    }
}