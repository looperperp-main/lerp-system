package com.l.erp.migracaoservice.services;

import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.domain.ExportBatch;
import com.l.erp.migracaoservice.infra.config.ExportProperties;
import com.l.erp.migracaoservice.repository.ExportBatchRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Manutenção do export (spec §7): expira e apaga arquivos vencidos, destrava batches que ficaram
 * em PROCESSING e reenfileira os que aguardam retry. Cada passo é idempotente (UPDATE condicional
 * no status), então duas instâncias rodando juntas não duplicam trabalho.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportMaintenanceService {

    private final ExportBatchRepository repo;
    private final ExportExtractionService extracao;
    private final ExportProperties props;
    private final MeterRegistry metricas;
    private final ExportNotificationService notificacao;

    /**
     * E-mail "24h antes de expirar" (spec §7). Só para quem ainda não baixou (READY): quem já baixou tem o arquivo.
     * A reserva é um UPDATE condicional ANTES do envio: duas instâncias nunca avisam em dobro. O custo é aviso
     * perdido se a publicação falhar logo em seguida (at-most-once, aceito: o aviso é cortesia, não obrigação).
     */
    @Scheduled(cron = "${syax.export.cron-aviso-expiracao:0 0 * * * *}")
    public void avisarExpiracao() {
        for (ExportBatch b : repo.listarParaAvisoDeExpiracao(Constants.EXPORT_AVISO_EXPIRACAO_HORAS, Constants.EXPORT_LOTE_MANUTENCAO)) {
            try {
                if (repo.marcarAvisoEnviado(b.id())) {
                    notificacao.avisarExpiracao(b);
                    metricas.counter("export_manutencao_total", "acao", "aviso_expiracao").increment();
                }
            } catch (RuntimeException e) {   // um batch ruim não trava o lote
                log.error("Falha ao avisar a expiração da exportação {} do tenant {}", b.id(), b.tenantId(), e);
            }
        }
    }

    @Scheduled(cron = "${syax.export.cron-expiracao:0 0 3 * * *}")
    public void expirar() {
        for (ExportBatch b : repo.listarExpirados(Constants.EXPORT_LOTE_MANUTENCAO)) {
            try {
                FileSystemUtils.deleteRecursively(props.baseDir().resolve(String.valueOf(b.tenantId())).resolve(b.id().toString()));
                repo.marcarExpirado(b.id());
                repo.registrarAuditoria(b.id(), b.tenantId(), Constants.EXPORT_AUDIT_EXPIRED, null);
                metricas.counter("export_manutencao_total", "acao", "expirado").increment();
            } catch (IOException | RuntimeException e) {   // um batch ruim não pode travar o lote; tenta de novo na próxima rodada
                log.error("Falha ao expirar a exportação {} do tenant {}", b.id(), b.tenantId(), e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${syax.export.intervalo-recuperacao-ms:60000}")
    public void recuperar() {
        for (ExportBatch b : repo.listarTravados(Constants.EXPORT_TIMEOUT_PROCESSAMENTO_MIN, Constants.EXPORT_LOTE_MANUTENCAO)) {
            boolean temTentativa = b.attemptCount() < Constants.EXPORT_MAX_TENTATIVAS;
            // O UPDATE só age se o batch ainda estiver em PROCESSING; false = a execução terminou enquanto olhávamos.
            if (repo.reenfileirarOuFalhar(b.id(), Constants.EXPORT_MAX_TENTATIVAS, Constants.EXPORT_FALHA_TIMEOUT)) {
                log.warn("Exportação {} do tenant {} passou de {} min em PROCESSING (tentativa {}): {}", b.id(), b.tenantId(),
                        Constants.EXPORT_TIMEOUT_PROCESSAMENTO_MIN, b.attemptCount(), temTentativa ? "reenfileirada" : "marcada FAILED");
                metricas.counter("export_manutencao_total", "acao", "timeout").increment();
                if (temTentativa) extracao.executar(b.id(), b.tenantId());
            }
        }
        // REQUESTED esquecido (reinício do serviço) ou em espera de retry: reexecuta; marcarProcessando garante execução única.
        for (ExportBatch b : repo.listarPendentesAntigos(Constants.EXPORT_ESPERA_RETRY_MIN, Constants.EXPORT_LOTE_MANUTENCAO)) {
            extracao.executar(b.id(), b.tenantId());
            metricas.counter("export_manutencao_total", "acao", "reexecutado").increment();
        }
    }
}
