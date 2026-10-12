package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.domain.ImportBatch;
import com.l.erp.migracaoservice.infra.config.AsyncConfig;
import com.l.erp.migracaoservice.infra.config.ImportProperties;
import com.l.erp.migracaoservice.repository.ImportBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Promoção assíncrona (spec migracao §3.2 e §11): grava o batch inteiro no cadastro numa transação só. Ou entra
 * tudo ou não entra nada: qualquer falha desfaz o batch e o marca FAILED com mensagem ao cliente. O DONE é gravado
 * na mesma transação do destino, então não existe "dados gravados mas batch preso em PROMOTING". O @Async engole
 * exceção, por isso o try/catch aqui é o que impede o batch de ficar preso.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportPromotionService {

    private final ImportBatchRepository batches;
    private final List<Promotor> promotores;
    private final TransactionTemplate tx;
    private final ImportProperties props;

    /** Chamado depois de iniciarPromocao (READY → PROMOTING); roda no executor de import. */
    @Async(AsyncConfig.IMPORT_EXECUTOR)
    public void promover(UUID batchId, Long tenantId, UUID userId) {
        try {
            ImportBatch batch = batches.buscar(batchId, tenantId).orElseThrow();
            Promotor promotor = promotores.stream().filter(p -> p.entidade().equals(batch.entityType())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Sem promotor para a entidade " + batch.entityType()));
            tx.executeWithoutResult(status -> {
                promotor.promover(tenantId, userId, batchId);
                if (!batches.concluirPromocao(batchId, tenantId, props.retentionDays())) {
                    // O timeout (spec §12.1) já marcou FAILED enquanto promovíamos: desfaz o destino para ficar coerente.
                    throw new IllegalStateException("Batch " + batchId + " não está mais em PROMOTING");
                }
            });
        } catch (Exception e) {
            log.error("[import {}] falha na promoção; nada foi gravado no cadastro", batchId, e);
            batches.falhar(batchId, tenantId, Constants.IMPORT_FALHA_PROMOCAO);
        }
    }
}
