package com.l.erp.migracaoservice.services;

import com.l.erp.migracaoservice.domain.ExportBatch;
import com.l.erp.migracaoservice.repository.ExportBatchRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** O evento carrega o id do usuário (nunca um e-mail) e só o export de cancelamento leva o link de posse. */
class ExportNotificationServiceTest {

    private static final Long TENANT = 7L;
    private static final UUID ID = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final OffsetDateTime EXPIRA = OffsetDateTime.parse("2026-10-19T13:00:00Z");

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final ExportBatchRepository repo = mock(ExportBatchRepository.class);
    private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();
    private ExportNotificationService service;

    @BeforeEach
    void setUp() {
        service = new ExportNotificationService(kafka, repo, JsonMapper.builder().build(), metricas, "https://api.syax.com.br/");
    }

    private static ExportBatch batch(String reason) {
        return new ExportBatch(ID, TENANT, USER, "{}", "XLSX", "READY", "{}", "{}", 1, reason, null,
                OffsetDateTime.now(), OffsetDateTime.now(), EXPIRA, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> publicado() throws Exception {
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("migracao.email.notification"), eq(USER.toString()), json.capture());
        return JsonMapper.builder().build().readValue(json.getValue(), Map.class);
    }

    private void kafkaResponde(CompletableFuture<SendResult<String, String>> futuro) {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(futuro);
    }

    @Test
    void prontoNormal_publicaSoOIdDoUsuarioESemLinkDePosse() throws Exception {
        kafkaResponde(CompletableFuture.completedFuture(null));

        service.avisarPronto(batch("ROUTINE_BACKUP"));

        Map<String, Object> e = publicado();
        assertThat(e).containsEntry("type", "EXPORT_PRONTO").containsEntry("userId", USER.toString())
                .containsEntry("batchId", ID.toString()).containsEntry("expiresAt", "2026-10-19T13:00:00Z");
        assertThat(((Number) e.get("tenantId")).longValue()).isEqualTo(TENANT);
        assertThat(e).doesNotContainKeys("downloadUrl", "email");        // jamais um e-mail: o destino é o cadastrado
        verify(repo, never()).tokenDe(ID);
        assertThat(metricas.counter("export_email_total", "tipo", "EXPORT_PRONTO", "resultado", "publicado").count()).isEqualTo(1.0);
    }

    @Test
    void cancelamento_levaOLinkDePosseComAUrlPublicaSemBarraDupla() throws Exception {
        kafkaResponde(CompletableFuture.completedFuture(null));
        when(repo.tokenDe(ID)).thenReturn(Optional.of("TOKEN43"));

        service.avisarPronto(batch("CANCELLATION"));

        assertThat(publicado()).containsEntry("downloadUrl", "https://api.syax.com.br/migracao/exports/posse/TOKEN43");
    }

    @Test
    void avisoDeExpiracao_usaOutroTipo() throws Exception {
        kafkaResponde(CompletableFuture.completedFuture(null));

        service.avisarExpiracao(batch(null));

        assertThat(publicado()).containsEntry("type", "EXPORT_EXPIRA_EM_BREVE");
    }

    @Test
    void falhaAssincronaDoKafka_viraMetricaELogNaoExcecao() {
        kafkaResponde(CompletableFuture.failedFuture(new IllegalStateException("broker fora")));

        service.avisarPronto(batch(null));          // não lança: o export já está pronto

        assertThat(metricas.counter("export_email_total", "tipo", "EXPORT_PRONTO", "resultado", "falha").count()).isEqualTo(1.0);
    }

    @Test
    void falhaSincronaDoProducer_tambemNaoLanca() {
        when(kafka.send(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("producer fechado"));

        service.avisarExpiracao(batch(null));

        assertThat(metricas.counter("export_email_total", "tipo", "EXPORT_EXPIRA_EM_BREVE", "resultado", "falha").count()).isEqualTo(1.0);
    }

    @Test
    void cancelamentoSemTokenPublicaSemLinkEmVezDeFalhar() throws Exception {
        kafkaResponde(CompletableFuture.completedFuture(null));
        when(repo.tokenDe(ID)).thenReturn(Optional.empty());

        service.avisarPronto(batch("CANCELLATION"));

        assertThat(publicado()).doesNotContainKey("downloadUrl");
    }
}
