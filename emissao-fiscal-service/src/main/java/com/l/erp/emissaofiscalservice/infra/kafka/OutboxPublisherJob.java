package com.l.erp.emissaofiscalservice.infra.kafka;

import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.domain.OutboxEvento;
import com.l.erp.emissaofiscalservice.repository.OutboxEventoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Publica {@code emissao.outbox_evento} pendente no Kafka — spec §3 item 10 (transactional outbox,
 * piloto no reator). Poll curto com {@code FOR UPDATE SKIP LOCKED}: mais de uma instância pode
 * rodar ao mesmo tempo sem publicar a mesma linha duas vezes.
 *
 * <p>Entrega é at-least-once, não exactly-once — um crash entre o ack do Kafka e marcar
 * {@code publicadoEm} reenvia no próximo poll; o consumidor do evento precisa ser idempotente
 * (dedupe pelo {@code id} do evento), garantia padrão de qualquer consumidor Kafka.</p>
 */
@Component
public class OutboxPublisherJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherJob.class);
    private static final int TAMANHO_LOTE = 50;

    private final OutboxEventoRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisherJob(OutboxEventoRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(cron = "${emissao.outbox.cron-publicacao}")
    @Transactional
    public void executar() {
        List<OutboxEvento> pendentes = repository.buscarPendentesComLock(PageRequest.of(0, TAMANHO_LOTE));

        for (OutboxEvento evento : pendentes) {
            try {
                kafkaTemplate.send(Constants.EMISSAO_DOCUMENTO_EVENTO_TOPIC, evento.getDocumentoId().toString(), evento.getPayload());
                evento.setPublicadoEm(OffsetDateTime.now());
                repository.save(evento);
            } catch (Exception e) {
                log.error("Falha ao publicar outbox_evento {} — fica pra próxima rodada do poll", evento.getId(), e);
            }
        }
    }
}
