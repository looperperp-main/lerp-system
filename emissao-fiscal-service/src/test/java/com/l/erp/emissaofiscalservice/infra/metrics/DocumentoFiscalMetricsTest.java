package com.l.erp.emissaofiscalservice.infra.metrics;

import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.DocumentoFiscalRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Um gauge por estado, lendo a contagem do repositório no momento da leitura. */
class DocumentoFiscalMetricsTest {

    @Test
    void registraUmGaugePorEstadoComAContagemAtual() {
        var repository = mock(DocumentoFiscalRepository.class);
        when(repository.countByStatus(StatusDocumentoFiscal.ERRO)).thenReturn(3L);
        var registry = new SimpleMeterRegistry();

        new DocumentoFiscalMetrics(repository).bindTo(registry);

        assertEquals(3.0, registry.get(Constants.EMISSAO_METRICA_DOCUMENTOS).tag("status", "ERRO").gauge().value());
        assertEquals(0.0, registry.get(Constants.EMISSAO_METRICA_DOCUMENTOS).tag("status", "AUTORIZADO").gauge().value());
        assertEquals(StatusDocumentoFiscal.values().length, registry.getMeters().size());
    }
}
