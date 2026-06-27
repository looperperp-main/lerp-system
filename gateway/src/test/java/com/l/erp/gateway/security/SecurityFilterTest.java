package com.l.erp.gateway.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityFilterTest {

    private static final String SECRET = "test-secret-with-at-least-32-characters-long!!";
    private static final String ISSUER = "L-ERP-auth-service";

    private SecurityFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SecurityFilter();
        ReflectionTestUtils.setField(filter, "secret", SECRET);
        SecurityContextHolder.clearContext();
    }

    private String tokenTenant() {
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject("user-1")
                .withClaim("loginType", "TENANT")
                .withClaim("tenantId", 42L)
                .withClaim("isOwner", true)
                .sign(Algorithm.HMAC256(SECRET));
    }

    /** FilterChain que captura a request encaminhada (ou null se não chamado). */
    private static class CapturingChain implements FilterChain {
        HttpServletRequest forwarded;
        boolean called;
        @Override public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            called = true;
            forwarded = (HttpServletRequest) req;
        }
    }

    @Test
    void optionsPreflight_returns200_semChamarChain() throws Exception {
        var req = new MockHttpServletRequest("OPTIONS", "/cadastro/api/v1/produtos");
        var res = new MockHttpServletResponse();
        var chain = new CapturingChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.called).isFalse();
    }

    @Test
    void publicPath_passaSemToken() throws Exception {
        var req = new MockHttpServletRequest("POST", "/auth/login");
        var res = new MockHttpServletResponse();
        var chain = new CapturingChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.called).isTrue();
    }

    @Test
    void semAuthorizationHeader_emPathProtegido_retorna401() throws Exception {
        var req = new MockHttpServletRequest("GET", "/cadastro/api/v1/produtos");
        var res = new MockHttpServletResponse();
        var chain = new CapturingChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(chain.called).isFalse();
    }

    @Test
    void tokenValido_injetaHeadersDeIdentidade_eEncaminha() throws Exception {
        var req = new MockHttpServletRequest("GET", "/cadastro/api/v1/produtos");
        req.addHeader("Authorization", "Bearer " + tokenTenant());
        var res = new MockHttpServletResponse();
        var chain = new CapturingChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.called).isTrue();
        assertThat(chain.forwarded.getHeader("X-User-Id")).isEqualTo("user-1");
        assertThat(chain.forwarded.getHeader("X-Tenant-Id")).isEqualTo("42");
        assertThat(chain.forwarded.getHeader("X-Is-Owner")).isEqualTo("true");
    }

    @Test
    void headerInternoForjadoPeloCliente_eDescartado() throws Exception {
        var req = new MockHttpServletRequest("GET", "/cadastro/api/v1/produtos");
        req.addHeader("Authorization", "Bearer " + tokenTenant());
        req.addHeader("X-Tenant-Id", "999"); // tenant forjado pelo cliente
        var res = new MockHttpServletResponse();
        var chain = new CapturingChain();

        filter.doFilter(req, res, chain);

        // o wrapper só expõe o tenant derivado do JWT, nunca o forjado
        assertThat(chain.forwarded.getHeader("X-Tenant-Id")).isEqualTo("42");
    }

    @Test
    void tokenExpirado_retorna401_comHeaderTokenExpired() throws Exception {
        String expired = JWT.create()
                .withIssuer(ISSUER)
                .withSubject("user-1")
                .withClaim("loginType", "TENANT")
                .withClaim("tenantId", 42L)
                .withExpiresAt(new java.util.Date(System.currentTimeMillis() - 1000))
                .sign(Algorithm.HMAC256(SECRET));
        var req = new MockHttpServletRequest("GET", "/cadastro/api/v1/produtos");
        req.addHeader("Authorization", "Bearer " + expired);
        var res = new MockHttpServletResponse();
        var chain = new CapturingChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getHeader("X-Token-Expired")).isEqualTo("true");
        assertThat(chain.called).isFalse();
    }

    @Test
    void tokenComAssinaturaInvalida_retorna401() throws Exception {
        String forged = JWT.create()
                .withIssuer(ISSUER)
                .withSubject("user-1")
                .withClaim("loginType", "TENANT")
                .sign(Algorithm.HMAC256("outro-secret-com-32-caracteres-de-tamanho!"));
        var req = new MockHttpServletRequest("GET", "/cadastro/api/v1/produtos");
        req.addHeader("Authorization", "Bearer " + forged);
        var res = new MockHttpServletResponse();
        var chain = new CapturingChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(chain.called).isFalse();
    }

    @Test
    void loginPartner_emPathNaoPermitido_retorna403() throws Exception {
        String partnerToken = JWT.create()
                .withIssuer(ISSUER)
                .withSubject("partner-1")
                .withClaim("loginType", "PARTNER")
                .withClaim("partnerId", "p-1")
                .sign(Algorithm.HMAC256(SECRET));
        var req = new MockHttpServletRequest("GET", "/cadastro/api/v1/produtos");
        req.addHeader("Authorization", "Bearer " + partnerToken);
        var res = new MockHttpServletResponse();
        var chain = new CapturingChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(chain.called).isFalse();
    }
}