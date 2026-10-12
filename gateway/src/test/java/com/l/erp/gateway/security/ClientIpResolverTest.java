package com.l.erp.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private static MockHttpServletRequest req(String remoto, String xff) {
        var r = new MockHttpServletRequest();
        r.setRemoteAddr(remoto);
        if (xff != null) r.addHeader("X-Forwarded-For", xff);
        return r;
    }

    @Test
    void semProxyConfiavelUsaOIpDaConexaoEIgnoraXff() {
        assertThat(ClientIpResolver.resolve(req("203.0.113.9", "1.1.1.1"), Set.of())).isEqualTo("203.0.113.9");
    }

    @Test
    void proxyConfiavelDelegaAoPrimeiroIpDoXff() {
        assertThat(ClientIpResolver.resolve(req("10.0.0.5", "198.51.100.7, 10.0.0.5"), Set.of("10.0.0.5"))).isEqualTo("198.51.100.7");
    }

    @Test
    void proxyConfiavelSemXffOuComXffEmBrancoCaiNoIpDaConexao() {
        assertThat(ClientIpResolver.resolve(req("10.0.0.5", null), Set.of("10.0.0.5"))).isEqualTo("10.0.0.5");
        assertThat(ClientIpResolver.resolve(req("10.0.0.5", "  "), Set.of("10.0.0.5"))).isEqualTo("10.0.0.5");
    }

    @Test
    void parseDaListaDeProxiesIgnoraVaziosEEspacos() {
        assertThat(ClientIpResolver.parseTrustedProxies(" 10.0.0.1 , ,10.0.0.2")).containsExactlyInAnyOrder("10.0.0.1", "10.0.0.2");
        assertThat(ClientIpResolver.parseTrustedProxies("")).isEmpty();
        assertThat(ClientIpResolver.parseTrustedProxies(null)).isEmpty();
    }
}
