package com.l.erp.migracaoservice.services;

import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.domain.ExportBatch;
import com.l.erp.migracaoservice.repository.ExportBatchRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * E-mails do export (spec §7): "pronto" e "24h antes de expirar". O migracao-service não tem SMTP nem a
 * tabela de usuários, então publica o evento em migracao.email.notification com o id do usuário e o
 * auth-service resolve o endereço e envia. Mandar só o id (e nunca um e-mail) é o que garante a salvaguarda
 * nº 2 da §10.1: o destino é sempre o cadastrado, jamais um informado no pedido.
 *
 * Nunca lança: e-mail é aviso, não pode derrubar nem retentar a geração do export. Falha de publicação
 * vira log + métrica (o cliente ainda vê o export na tela).
 */
@Service
@Slf4j
public class ExportNotificationService {

    private final KafkaTemplate<String, String> kafka;
    private final ExportBatchRepository repo;
    private final ObjectMapper mapper;
    private final MeterRegistry metricas;
    private final String publicBaseUrl;

    public ExportNotificationService(KafkaTemplate<String, String> kafka, ExportBatchRepository repo, ObjectMapper mapper,
                                     MeterRegistry metricas,
                                     @Value("${syax.export.public-base-url}") String publicBaseUrl) {
        this.kafka = kafka;
        this.repo = repo;
        this.mapper = mapper;
        this.metricas = metricas;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    public void avisarPronto(ExportBatch batch) {
        publicar(Constants.EXPORT_EMAIL_PRONTO, batch);
    }

    public void avisarExpiracao(ExportBatch batch) {
        publicar(Constants.EXPORT_EMAIL_EXPIRA_EM_BREVE, batch);
    }

    private void publicar(String tipo, ExportBatch batch) {
        try {
            Map<String, Object> evento = new HashMap<>();
            evento.put("type", tipo);
            evento.put("userId", batch.requestedBy().toString());
            evento.put("tenantId", batch.tenantId());
            evento.put("batchId", batch.id().toString());
            evento.put("expiresAt", batch.expiresAt() == null ? null : batch.expiresAt().toInstant().toString());
            // Só o export de cancelamento leva o link de posse (sem sessão); os demais abrem a tela logado.
            if (Constants.EXPORT_MOTIVO_CANCELAMENTO.equals(batch.reason())) {
                repo.tokenDe(batch.id()).ifPresent(token -> evento.put("downloadUrl", publicBaseUrl + Constants.EXPORT_POSSE_PREFIXO + token));
            }
            kafka.send(Constants.EXPORT_TOPIC_EMAIL, batch.requestedBy().toString(), mapper.writeValueAsString(evento))
                    .whenComplete((resultado, erro) -> {
                        if (erro != null) falhou(tipo, batch, erro);
                        else metricas.counter("export_email_total", "tipo", tipo, "resultado", "publicado").increment();
                    });
        } catch (RuntimeException e) {   // serialização ou producer indisponível: aviso perdido, export intacto
            falhou(tipo, batch, e);
        }
    }

    private void falhou(String tipo, ExportBatch batch, Throwable erro) {
        log.error("Falha ao publicar o e-mail {} da exportação {} do tenant {}", tipo, batch.id(), batch.tenantId(), erro);
        metricas.counter("export_email_total", "tipo", tipo, "resultado", "falha").increment();
    }
}
