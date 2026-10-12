package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.infra.config.ImportProperties;
import com.l.erp.migracaoservice.repository.ImportBatchRepository;
import com.l.erp.migracaoservice.repository.ImportBatchRepository.Purgavel;
import com.l.erp.migracaoservice.repository.ImportRowRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;

/**
 * Manutenção do import (spec migracao §12 e §12.1): destrava batch parado em VALIDATING/PROMOTING e descarta o
 * staging vencido (linhas + arquivo original, que guardam dado pessoal de terceiros). Cada passo é um UPDATE
 * condicional, então duas instâncias rodando juntas não duplicam trabalho.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportMaintenanceService {

    private final ImportBatchRepository batches;
    private final ImportRowRepository rows;
    private final ImportProperties props;
    private final MeterRegistry metricas;

    /** A spec põe o timeout no job diário das 3h30; a cada minuto o cliente não espera até 24 h por um batch preso. */
    @Scheduled(fixedDelayString = "${syax.import.intervalo-recuperacao-ms:60000}")
    public void destravar() {
        contar("timeout_validacao", batches.expirarTravados(Constants.IMPORT_STATUS_VALIDATING,
                Constants.IMPORT_TIMEOUT_VALIDACAO_MIN, Constants.IMPORT_FALHA_TIMEOUT_VALIDACAO));
        contar("timeout_promocao", batches.expirarTravados(Constants.IMPORT_STATUS_PROMOTING,
                Constants.IMPORT_TIMEOUT_PROMOCAO_MIN, Constants.IMPORT_FALHA_TIMEOUT_PROMOCAO));
    }

    @Scheduled(cron = "${syax.import.cron-purga:0 30 3 * * *}")
    public void purgar() {
        for (Purgavel b : batches.listarParaPurgar(Constants.IMPORT_LOTE_MANUTENCAO)) {
            try {
                FileSystemUtils.deleteRecursively(props.baseDir().resolve(String.valueOf(b.tenantId())).resolve(b.id().toString()));
                rows.purgarLinhas(b.id(), b.tenantId());
                batches.marcarPurgado(b.id(), b.tenantId(), Constants.IMPORT_FALHA_RETENCAO);
                contar("purgado", 1);
            } catch (IOException | RuntimeException e) {   // um batch ruim não trava o lote; tenta de novo na próxima rodada
                log.error("Falha ao purgar o staging do batch {} do tenant {}", b.id(), b.tenantId(), e);
            }
        }
    }

    private void contar(String acao, int quantidade) {
        if (quantidade > 0) {
            log.warn("Manutenção do import: {} batch(es) em '{}'", quantidade, acao);
            metricas.counter("import_manutencao_total", "acao", acao).increment(quantidade);
        }
    }
}
