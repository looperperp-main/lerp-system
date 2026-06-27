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
        var filter = new RateLimitFilter(5, 5);
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
        var filter = new RateLimitFilter(1, 1); // capacidade 1
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
        var filter = new RateLimitFilter(1, 1);
        var chain = new CountingChain();

        filter.doFilter(reqFrom("1.1.1.1"), new MockHttpServletResponse(), chain);
        var res = new MockHttpServletResponse();
        filter.doFilter(reqFrom("2.2.2.2"), res, chain);

        assertThat(chain.calls).isEqualTo(2); // cada IP tem seu próprio limite
        assertThat(res.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest reqFrom(String ip) {
        var req = new MockHttpServletRequest();
        req.setRemoteAddr(ip);
        return req;
    }
}