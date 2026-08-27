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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter implements Filter {

    // ponytail: LRU limitado por nº de entradas (sem dependência nova) — garante teto de heap
    // mesmo se a allowlist de proxy for mal configurada. Subir pra cache com TTL se 50k não bastar.
    private static final int MAX_BUCKETS = 50_000;

    private final Map<String, Bucket> buckets = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                    return size() > MAX_BUCKETS;
                }
            });
    // Bucket dedicado, mais restrito, para os paths públicos que rodam Argon2 memory-hard por
    // chamada (7.3/7.12, spec/auditoria.md): criar-conta (+ 2 INSERTs) e os 3 logins (encode
    // já roda antes de qualquer CAPTCHA existir). /auth/refresh e /auth/logout ficam de fora —
    // não tocam em hash de senha.
    private static final Set<String> ARGON2_PATHS = Set.of(
            "/auth/criar-conta", "/auth/login", "/auth/tenant/login", "/auth/partner/login"
    );

    private final Map<String, Bucket> argon2Buckets = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                    return size() > MAX_BUCKETS;
                }
            });

    private final int replenishRate;
    private final int burstCapacity;
    private final int argon2BurstCapacity;
    private final int argon2ReplenishRate;
    private final Set<String> trustedProxies;

    public RateLimitFilter(
            @Value("${rate-limit.replenish-rate:20}") int replenishRate,
            @Value("${rate-limit.burst-capacity:40}") int burstCapacity,
            @Value("${gateway.trusted-proxies:}") String trustedProxiesRaw,
            @Value("${rate-limit.argon2.burst-capacity:5}") int argon2BurstCapacity,
            @Value("${rate-limit.argon2.replenish-rate:5}") int argon2ReplenishRate) {
        this.replenishRate = replenishRate;
        this.burstCapacity = burstCapacity;
        this.argon2BurstCapacity = argon2BurstCapacity;
        this.argon2ReplenishRate = argon2ReplenishRate;
        this.trustedProxies = Arrays.stream(trustedProxiesRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (httpRequest.getRequestURI().startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }

        String key = resolveKey(httpRequest);

        if (ARGON2_PATHS.contains(httpRequest.getRequestURI())) {
            Bucket argon2Bucket = argon2Buckets.computeIfAbsent(key, k -> createArgon2Bucket());
            if (!argon2Bucket.tryConsume(1)) {
                respondTooManyRequests(httpResponse);
                return;
            }
        }

        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            respondTooManyRequests(httpResponse);
        }
    }

    private void respondTooManyRequests(HttpServletResponse httpResponse) throws IOException {
        httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        httpResponse.getWriter().write(
                "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Muitas requisições em pouco tempo. Aguarde alguns instantes e tente novamente.\"}"
        );
    }

    private Bucket createBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(burstCapacity)
                        .refillGreedy(replenishRate, Duration.ofSeconds(1))
                        .build())
                .build();
    }

    // Janela de 10 min (vs. 1s do bucket genérico): esses paths rodam Argon2 memory-hard por
    // chamada, então o teto tem que ser baixo o bastante pra não sustentar flood mesmo dentro
    // do burst do limite genérico acima.
    private Bucket createArgon2Bucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(argon2BurstCapacity)
                        .refillGreedy(argon2ReplenishRate, Duration.ofMinutes(10))
                        .build())
                .build();
    }

    // Só confia em X-Forwarded-For quando a conexão vem de um IP explicitamente listado em
    // gateway.trusted-proxies (o LB/nginx real, não a faixa RFC1918 inteira) — senão qualquer
    // chamador nessa faixa forja o header e anula o rate-limit (spec/auditoria.md §7.2).
    // Default vazio = nunca confia em XFF = seguro em dev local sem precisar configurar nada.
    private String resolveKey(HttpServletRequest request) {
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