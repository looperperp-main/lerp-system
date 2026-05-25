package com.l.erp.gateway.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter implements Filter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int replenishRate;
    private final int burstCapacity;

    public RateLimitFilter(
            @Value("${rate-limit.replenish-rate:20}") int replenishRate,
            @Value("${rate-limit.burst-capacity:40}") int burstCapacity) {
        this.replenishRate = replenishRate;
        this.burstCapacity = burstCapacity;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String key = resolveKey(httpRequest);
        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            httpResponse.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Try again later.\"}"
            );
        }
    }

    private Bucket createBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(burstCapacity)
                        .refillGreedy(replenishRate, Duration.ofSeconds(1))
                        .build())
                .build();
    }

    // IPs de redes privadas RFC 1918 e loopback — considerados proxies confiáveis (nginx, OCI LB)
    // Recomendação: verificar X-Forwarded-For somente quando a conexão vier de um proxy interno.
    private static final Set<String> PRIVATE_PREFIXES = Set.of(
            "10.", "127.", "192.168.",
            "172.16.", "172.17.", "172.18.", "172.19.", "172.20.",
            "172.21.", "172.22.", "172.23.", "172.24.", "172.25.",
            "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31."
    );

    private String resolveKey(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (isTrustedProxy(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String addr) {
        return PRIVATE_PREFIXES.stream().anyMatch(addr::startsWith);
    }
}