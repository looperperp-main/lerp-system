package com.l.erp.billingservice.infra.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class InternalRequestFilterTest {

    private static final String INTERNAL_SECRET = "test-internal-secret";

    private final InternalRequestFilter filter = new InternalRequestFilter();

    {
        ReflectionTestUtils.setField(filter, "internalSecret", INTERNAL_SECRET);
    }

    private static class CountingChain implements FilterChain {
        int calls;
        @Override public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            calls++;
        }
    }

    @AfterEach
    void limpaSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void semHeaderXUserId_emPathProtegido_retorna401() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/v1/subscriptions");
        req.addHeader("X-Internal-Secret", INTERNAL_SECRET);
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("Unauthorized");
        assertThat(chain.calls).isZero();
    }

    @Test
    void semSegredoInterno_emPathProtegido_retorna401() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/v1/subscriptions");
        req.addHeader("X-User-Id", "user-1");
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(chain.calls).isZero();
    }

    @Test
    void comHeaderXUserId_encaminha() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/v1/subscriptions");
        req.addHeader("X-User-Id", "user-1");
        req.addHeader("X-Internal-Secret", INTERNAL_SECRET);
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.calls).isEqualTo(1);
    }

    @Test
    void webhookAsaas_passaSemHeader() throws Exception {
        var req = new MockHttpServletRequest("POST", "/api/v1/webhooks/asaas");
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.calls).isEqualTo(1);
    }

    @Test
    void plans_passaSemHeader() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/v1/plans");
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.calls).isEqualTo(1);
    }

    @Test
    void postPartners_passaSemHeader() throws Exception {
        var req = new MockHttpServletRequest("POST", "/api/v1/partners");
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.calls).isEqualTo(1);
    }

    @Test
    void getPartners_semHeader_retorna401() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/v1/partners");
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(chain.calls).isZero();
    }

    @Test
    void segredoInternoInvalido_emPathProtegido_retorna401() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/v1/subscriptions");
        req.addHeader("X-User-Id", "user-1");
        req.addHeader("X-Internal-Secret", "segredo-forjado");
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(chain.calls).isZero();
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
    void actuatorInfo_passaSemHeader() throws Exception {
        var req = new MockHttpServletRequest("GET", "/actuator/info");
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.calls).isEqualTo(1);
    }

    @Test
    void actuatorPrometheus_passaSemHeader() throws Exception {
        var req = new MockHttpServletRequest("GET", "/actuator/prometheus");
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.calls).isEqualTo(1);
    }

    @Test
    void actuatorLoggers_passaSemHeader() throws Exception {
        var req = new MockHttpServletRequest("GET", "/actuator/loggers/com.l.erp");
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
    void pathDoSwaggerUi_passaSemHeader() throws Exception {
        var req = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.calls).isEqualTo(1);
    }
}
