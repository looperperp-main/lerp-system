package com.l.erp.common.infra.filter;

import com.l.erp.common.util.Constants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Popula o MDC com o correlationId da requisição (header {@code X-Correlation-ID}; gera um se ausente)
 * e o devolve no header da resposta. Assim a linha de log de erro e o corpo do {@code StandardError}
 * ficam amarrados à mesma operação — colável no Rastreador do admin.
 *
 * <p>ponytail: pra sair em TODA linha de log (não só nos erros), inclua {@code [%X{correlationId}]}
 * no pattern do logback de cada serviço; o valor já está no MDC.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(Constants.HEADER_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(Constants.MDC_CORRELATION_ID, correlationId);
        response.setHeader(Constants.HEADER_CORRELATION_ID, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(Constants.MDC_CORRELATION_ID);
        }
    }
}
