package com.l.erp.billingservice.services.webhook;

import com.l.erp.billingservice.repository.WebhookLogRepository;
import com.l.erp.common.util.Constants;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Gauge {@code webhook_pendente}: webhooks recebidos do Asaas que nunca finalizaram o
 * processamento (presos em {@code RECEBIDO} além do cutoff).
 *
 * <p>É o alarme financeiro do billing — a mesma condição que o {@link
 * com.l.erp.billingservice.services.recovery.WebhookRecoveryJob} usa pra reprocessar. Se este
 * número fica &gt;0 de forma sustentada, o recovery não está dando conta e há pagamento parado.</p>
 */
@Component
public class WebhookMetrics {

    public WebhookMetrics(WebhookLogRepository repository, MeterRegistry meterRegistry) {
        // O Micrometer guarda referência fraca ao objeto de estado; o repository é singleton do
        // contexto Spring, então não é coletado e o gauge não vira NaN.
        Gauge.builder(Constants.METRIC_WEBHOOK_PENDENTE, repository, WebhookMetrics::contarPresos)
                .description("Webhooks em " + Constants.WEBHOOK_RECEBIDO + " há mais de "
                        + Constants.WEBHOOK_STUCK_MINUTES + " min — >0 sustentado = pagamento parado")
                .register(meterRegistry);
    }

    // ponytail: COUNT a cada scrape (15s). Se pesar, cacheia por 1min ou alimenta um AtomicLong
    // num @Scheduled — só vale quando webhook_log crescer a ponto de o COUNT aparecer no perfil.
    private static double contarPresos(WebhookLogRepository repository) {
        return repository.countByStatusAndReceivedAtBefore(
                Constants.WEBHOOK_RECEBIDO,
                OffsetDateTime.now().minusMinutes(Constants.WEBHOOK_STUCK_MINUTES));
    }
}
