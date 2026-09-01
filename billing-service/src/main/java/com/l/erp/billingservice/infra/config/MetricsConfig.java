package com.l.erp.billingservice.infra.config;

import com.l.erp.common.util.Constants;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.ObservationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Adiciona a tag tenant_id às métricas http.server.requests, lida do header X-Tenant-Id
 * (injetado pelo gateway em produção; simulado diretamente no load test — ver
 * billing-service/loadtest/README.md).
 *
 * ponytail: tenant_id como label de Prometheus só é seguro aqui porque o load test controla
 * o número de tenants simulados (cardinalidade limitada). NÃO promover pra padrão de produção
 * multi-tenant real sem um teto de cardinalidade — mesma regra que já vale pro Loki (CLAUDE.md).
 */
@Configuration
public class MetricsConfig {

    @Bean
    public ObservationFilter tenantTagObservationFilter() {
        return context -> {
            if (context instanceof ServerRequestObservationContext serverContext) {
                String tenantId = extractTenantId(serverContext.getCarrier());
                if (tenantId != null) {
                    context.addLowCardinalityKeyValue(KeyValue.of(Constants.METRIC_TAG_TENANT, tenantId));
                }
            }
            return context;
        };
    }

    static String extractTenantId(HttpServletRequest request) {
        String value = request.getHeader(Constants.HEADER_TENANT_ID);
        return (value == null || value.isBlank()) ? null : value;
    }
}
