package com.l.erp.emissaofiscalservice.infra.metrics;

import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.DocumentoFiscalRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * {@code emissao_documentos{status}}: quantos documentos há em cada estado (todos os tenants, sem
 * label de tenant/documento — baixa cardinalidade). Alertas úteis: {@code ERRO} > 0 e {@code TRANSMITIDO}
 * que não cai. Lê do banco a cada scrape (contagem simples por status).
 *
 * <p>ponytail: só gauge de estado; contador de transições e tempo de job entram se o painel pedir.</p>
 */
@Component
public class DocumentoFiscalMetrics implements MeterBinder {

    private final DocumentoFiscalRepository repository;

    public DocumentoFiscalMetrics(DocumentoFiscalRepository repository) {
        this.repository = repository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (StatusDocumentoFiscal status : StatusDocumentoFiscal.values()) {
            Gauge.builder(Constants.EMISSAO_METRICA_DOCUMENTOS, repository, r -> r.countByStatus(status))
                    .tag("status", status.name())
                    .description("Documentos fiscais por estado")
                    .register(registry);
        }
    }
}
