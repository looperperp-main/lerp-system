package com.l.erp.gateway.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private static class CountingChain implements FilterChain {
        int calls;
        @Override public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            calls++;
        }
    }

    @Test
    void dentroDaCapacidade_passa() throws Exception {
        var filter = new RateLimitFilter(5, 5, "", 100, 100);
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("8.8.8.8");
        var res = new MockHttpServletResponse();
        var chain = new CountingChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.calls).isEqualTo(1);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void acimaDaCapacidade_retorna429() throws Exception {
        var filter = new RateLimitFilter(1, 1, "", 100, 100); // capacidade 1
        var chain = new CountingChain();

        // 1ª request consome o único token
        filter.doFilter(reqFrom("9.9.9.9"), new MockHttpServletResponse(), chain);
        // 2ª request estoura o limite
        var res = new MockHttpServletResponse();
        filter.doFilter(reqFrom("9.9.9.9"), res, chain);

        assertThat(chain.calls).isEqualTo(1); // só a primeira passou
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getContentAsString()).contains("Too Many Requests");
    }

    @Test
    void ipsDistintos_temBucketsIndependentes() throws Exception {
        var filter = new RateLimitFilter(1, 1, "", 100, 100);
        var chain = new CountingChain();

        filter.doFilter(reqFrom("1.1.1.1"), new MockHttpServletResponse(), chain);
        var res = new MockHttpServletResponse();
        filter.doFilter(reqFrom("2.2.2.2"), res, chain);

        assertThat(chain.calls).isEqualTo(2); // cada IP tem seu próprio limite
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void xForwardedFor_ignoradoSeOrigemNaoEhProxyConfiado() throws Exception {
        var filter = new RateLimitFilter(1, 1, "10.0.0.5", 100, 100); // só esse IP é proxy confiável
        var chain = new CountingChain();

        // origem privada mas fora da allowlist — XFF não deve ser usado como chave
        var req = reqFrom("172.19.0.7");
        req.addHeader("X-Forwarded-For", "1.2.3.4");
        filter.doFilter(req, new MockHttpServletResponse(), chain);

        var req2 = reqFrom("172.19.0.7");
        req2.addHeader("X-Forwarded-For", "5.6.7.8"); // XFF diferente, mesma origem real
        var res = new MockHttpServletResponse();
        filter.doFilter(req2, res, chain);

        assertThat(chain.calls).isEqualTo(1); // ambas contam pro mesmo bucket (remoteAddr real)
        assertThat(res.getStatus()).isEqualTo(429);
    }

    @Test
    void xForwardedFor_usadoSoQuandoOrigemEhProxyConfiado() throws Exception {
        var filter = new RateLimitFilter(1, 1, "10.0.0.5", 100, 100);
        var chain = new CountingChain();

        var req = reqFrom("10.0.0.5"); // proxy confiável
        req.addHeader("X-Forwarded-For", "1.2.3.4");
        filter.doFilter(req, new MockHttpServletResponse(), chain);

        var req2 = reqFrom("10.0.0.5");
        req2.addHeader("X-Forwarded-For", "5.6.7.8"); // cliente real diferente
        var res = new MockHttpServletResponse();
        filter.doFilter(req2, res, chain);

        assertThat(chain.calls).isEqualTo(2); // cada XFF vira bucket próprio
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void actuator_naoEhLimitado() throws Exception {
        var filter = new RateLimitFilter(1, 1, "", 100, 100);
        var chain = new CountingChain();

        var req = reqFrom("9.9.9.9");
        req.setRequestURI("/actuator/health");
        filter.doFilter(req, new MockHttpServletResponse(), chain);

        var req2 = reqFrom("9.9.9.9");
        req2.setRequestURI("/actuator/health");
        var res = new MockHttpServletResponse();
        filter.doFilter(req2, res, chain);

        assertThat(chain.calls).isEqualTo(2); // nenhuma das duas foi barrada
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void criarConta_temBucketDedicadoMaisRestrito() throws Exception {
        // limite genérico bem folgado (100) — quem barra aqui tem que ser o bucket dedicado
        var filter = new RateLimitFilter(100, 100, "", 1, 1); // criar-conta: capacidade 1
        var chain = new CountingChain();

        var req = reqFrom("9.9.9.9");
        req.setRequestURI("/auth/criar-conta");
        filter.doFilter(req, new MockHttpServletResponse(), chain);

        var req2 = reqFrom("9.9.9.9");
        req2.setRequestURI("/auth/criar-conta");
        var res = new MockHttpServletResponse();
        filter.doFilter(req2, res, chain);

        assertThat(chain.calls).isEqualTo(1); // só a primeira passou
        assertThat(res.getStatus()).isEqualTo(429);
    }

    @Test
    void criarConta_naoConsomeBucketGenericoDeOutrosPaths() throws Exception {
        var filter = new RateLimitFilter(1, 1, "", 100, 100); // genérico: capacidade 1
        var chain = new CountingChain();

        var req = reqFrom("9.9.9.9");
        req.setRequestURI("/auth/criar-conta");
        filter.doFilter(req, new MockHttpServletResponse(), chain); // consome o bucket genérico também

        var req2 = reqFrom("9.9.9.9");
        req2.setRequestURI("/cadastro/api/v1/produtos"); // path diferente, mesmo IP
        var res = new MockHttpServletResponse();
        filter.doFilter(req2, res, chain);

        assertThat(chain.calls).isEqualTo(1); // a 2ª estourou o bucket genérico compartilhado por IP
        assertThat(res.getStatus()).isEqualTo(429);
    }

    @Test
    void login_tambemUsaBucketDedicadoArgon2() throws Exception {
        // 7.12: /auth/login roda Argon2, mesmo bucket dedicado do criar-conta (7.3)
        var filter = new RateLimitFilter(100, 100, "", 1, 1);
        var chain = new CountingChain();

        var req = reqFrom("9.9.9.9");
        req.setRequestURI("/auth/login");
        filter.doFilter(req, new MockHttpServletResponse(), chain);

        var req2 = reqFrom("9.9.9.9");
        req2.setRequestURI("/auth/tenant/login"); // path diferente, mesmo bucket dedicado (por IP)
        var res = new MockHttpServletResponse();
        filter.doFilter(req2, res, chain);

        assertThat(chain.calls).isEqualTo(1);
        assertThat(res.getStatus()).isEqualTo(429);
    }

    private MockHttpServletRequest reqFrom(String ip) {
        var req = new MockHttpServletRequest();
        req.setRemoteAddr(ip);
        return req;
    }
}