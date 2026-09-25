package com.l.erp.emissaofiscalservice.services.certificado;

import com.l.erp.common.api.dto.AuditEventDTO;
import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.domain.CertificadoDigital;
import com.l.erp.emissaofiscalservice.infra.kafka.AuditProducerService;
import com.l.erp.emissaofiscalservice.repository.CertificadoDigitalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Alerta de vencimento do certificado A1 — spec §3 item 1: "job diário, não checagem em request".
 * Query indexada ({@code idx_certificado_vencimento_alerta}) sobre certificados ativos, ainda sem
 * alerta enviado, vencendo dentro da janela configurada — publica {@link AuditEventDTO} no Kafka
 * (mesmo tópico de auditoria já usado no reator) e marca {@code alerta_enviado=true}.
 *
 * <p>Sem lock distribuído de propósito: idempotente por natureza — mesmo rodando em duas
 * instâncias ao mesmo tempo, o pior caso é o alerta sair duplicado uma vez (nunca é perdido),
 * o que não justifica trazer Redis como dependência só para isso (mesma lógica do §3 item 3 pra
 * numeração).</p>
 */
@Component
public class CertificadoVencimentoJob {

    private static final Logger log = LoggerFactory.getLogger(CertificadoVencimentoJob.class);

    private final CertificadoDigitalRepository repository;
    private final AuditProducerService auditProducerService;

    @Value("${emissao.certificado.alerta-vencimento-dias:30}")
    private int diasAntesDoVencimento;

    public CertificadoVencimentoJob(CertificadoDigitalRepository repository, AuditProducerService auditProducerService) {
        this.repository = repository;
        this.auditProducerService = auditProducerService;
    }

    @Scheduled(cron = "${emissao.certificado.cron-alerta-vencimento}")
    @Transactional
    public void executar() {
        OffsetDateTime limite = OffsetDateTime.now().plusDays(diasAntesDoVencimento);
        List<CertificadoDigital> vencendo =
                repository.findByAtivoTrueAndAlertaEnviadoFalseAndCertificadoValidoAteLessThanEqual(limite);

        for (CertificadoDigital certificado : vencendo) {
            try {
                auditProducerService.sendAuditEvent(new AuditEventDTO(
                        Constants.AUDIT_ACTION_ALERTA_VENCIMENTO_CERTIFICADO,
                        null,
                        Constants.TARGET_TYPE_CERTIFICADO_DIGITAL,
                        certificado.getId(),
                        "SUCCESS",
                        "{\"emitenteId\":\"" + certificado.getEmitenteId() + "\",\"validoAte\":\""
                                + certificado.getCertificadoValidoAte() + "\"}",
                        null,
                        Instant.now()));
                certificado.setAlertaEnviado(true);
                repository.save(certificado);
            } catch (Exception e) {
                log.error("Falha ao publicar alerta de vencimento do certificado {}", certificado.getId(), e);
            }
        }
    }
}
