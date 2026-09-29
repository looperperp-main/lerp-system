package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.DocumentoFiscalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Varre {@code TRANSMITIDO} parados além da janela e delega ao {@link ReconciliacaoTransmissaoService}. Não é {@code @Transactional}. */
@Component
public class ReconciliacaoTransmissaoJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliacaoTransmissaoJob.class);
    private static final int TAMANHO_LOTE = 50;

    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final ReconciliacaoTransmissaoService service;

    public ReconciliacaoTransmissaoJob(DocumentoFiscalRepository documentoFiscalRepository,
                                       ReconciliacaoTransmissaoService service) {
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.service = service;
    }

    @Scheduled(cron = "${emissao.reconciliacao.cron}")
    public void executar() {
        OffsetDateTime limite = OffsetDateTime.now().minusMinutes(Constants.EMISSAO_RECONCILIACAO_JANELA_MINUTOS);
        for (UUID id : documentoFiscalRepository.buscarIdsParadosDesde(
                StatusDocumentoFiscal.TRANSMITIDO, limite, PageRequest.of(0, TAMANHO_LOTE))) {
            try {
                service.encerrarSeAindaPreso(id, limite);
            } catch (Exception falha) {
                log.error("Falha ao reconciliar o documento {}", id, falha);
            }
        }
    }
}
