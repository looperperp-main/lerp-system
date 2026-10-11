package com.l.erp.migracaoservice.infra.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

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
        var req = new MockHttpServletRequest("GET", "/migracao/export");
        req.addHeader("X-Internal-Secret", INTERNAL_SECRET);
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(chain.calls).isZero();
    }

    @Test
    void semSegredoInterno_emPathProtegido_retorna401() throws Exception {
        var req = new MockHttpServletRequest("GET", "/migracao/export");
        req.addHeader("X-User-Id", "user-1");
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(chain.calls).isZero();
    }

    @Test
    void segredoInternoInvalido_emPathProtegido_retorna401() throws Exception {
        var req = new MockHttpServletRequest("GET", "/migracao/export");
        req.addHeader("X-User-Id", "user-1");
        req.addHeader("X-Internal-Secret", "segredo-forjado");
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(chain.calls).isZero();
    }

    @Test
    void comSegredoEUsuario_encaminhaEPopulaAuthorities() throws Exception {
        var req = new MockHttpServletRequest("GET", "/migracao/export");
        req.addHeader("X-User-Id", "user-1");
        req.addHeader("X-Internal-Secret", INTERNAL_SECRET);
        req.addHeader("X-Authorities", "EXPORT_DADOS, IMPORT_DADOS");
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.calls).isEqualTo(1);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("EXPORT_DADOS", "IMPORT_DADOS");
    }

    @Test
    void actuatorHealth_passaSemHeader() throws Exception {
        var req = new MockHttpServletRequest("GET", "/actuator/health");
        var chain = new CountingChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(chain.calls).isEqualTo(1);
    }

    @Test
    void actuatorPrometheus_passaSemHeader() throws Exception {
        var req = new MockHttpServletRequest("GET", "/actuator/prometheus");
        var chain = new CountingChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(chain.calls).isEqualTo(1);
    }
}
