package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.DocumentoFiscalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Passo assíncrono {@code RASCUNHO → TRANSMITIDO → AUTORIZADO|REJEITADO} da NFS-e. Mesma forma do
 * {@link TransmissaoDocumentoJob}: poll curto, lock por documento, cada documento na própria transação.
 * Não é {@code @Transactional}.
 */
@Component
public class EmissaoNfseJob {

    private static final Logger log = LoggerFactory.getLogger(EmissaoNfseJob.class);
    private static final int TAMANHO_LOTE = 20;

    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final EmissaoNfseService emissaoNfseService;

    public EmissaoNfseJob(DocumentoFiscalRepository documentoFiscalRepository, EmissaoNfseService emissaoNfseService) {
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.emissaoNfseService = emissaoNfseService;
    }

    @Scheduled(cron = "${emissao.nfse.cron}")
    public void executar() {
        // TRANSMITIDO primeiro: retomada de quem já pode estar no ADN (timeout, crash) — consulta antes de reenviar
        for (UUID id : buscar(StatusDocumentoFiscal.TRANSMITIDO)) {
            try {
                emissaoNfseService.processar(id, false);
            } catch (Exception falha) {
                log.error("Falha inesperada ao retomar a NFS-e {}", id, falha);
            }
        }
        for (UUID id : buscar(StatusDocumentoFiscal.RASCUNHO)) {
            try {
                if (emissaoNfseService.iniciar(id)) {
                    emissaoNfseService.processar(id, true);
                }
            } catch (Exception falha) {
                log.error("Falha inesperada ao emitir a NFS-e {}", id, falha);
            }
        }
        cancelarPendentes();
    }

    private void cancelarPendentes() {
        for (UUID id : documentoFiscalRepository.buscarIdsComCancelamentoPendente(OffsetDateTime.now(), PageRequest.of(0, TAMANHO_LOTE))) {
            try {
                emissaoNfseService.cancelar(id);
            } catch (Exception falha) {
                log.error("Falha inesperada ao cancelar a NFS-e {}", id, falha);
            }
        }
    }

    private List<UUID> buscar(StatusDocumentoFiscal status) {
        return documentoFiscalRepository.buscarIdsProntosNfse(status, OffsetDateTime.now(), PageRequest.of(0, TAMANHO_LOTE));
    }
}
