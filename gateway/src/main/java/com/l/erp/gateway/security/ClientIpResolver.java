package com.l.erp.gateway.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * IP real do cliente. Só confia em X-Forwarded-For quando a conexão vem de um IP explicitamente listado em
 * gateway.trusted-proxies (o LB/nginx real, não a faixa RFC1918 inteira): senão qualquer chamador forja o
 * header (spec/auditoria/auditoria.md §7.2). Default vazio = nunca confia em XFF = seguro em dev local.
 * Usado pelo rate limit e pelo IP gravado na auditoria do download de posse do export.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {}

    /** "10.0.0.1, 10.0.0.2" → conjunto sem vazios; null/branco → vazio. */
    public static Set<String> parseTrustedProxies(String raw) {
        if (raw == null) return Set.of();
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    public static String resolve(HttpServletRequest request, Set<String> trustedProxies) {
        String remoteAddr = request.getRemoteAddr();
        if (trustedProxies.contains(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }
}
