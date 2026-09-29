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
 * Passo assíncrono {@code ASSINADO → TRANSMITIDO → AUTORIZADO|REJEITADO|DENEGADO} (spec §3 item 10).
 * Mesma forma do {@link AssinaturaDocumentoJob}: poll curto, lock por documento com {@code SKIP LOCKED},
 * cada documento na própria transação (falha de um não trava os demais). Não é {@code @Transactional}.
 */
@Component
public class TransmissaoDocumentoJob {

    private static final Logger log = LoggerFactory.getLogger(TransmissaoDocumentoJob.class);
    private static final int TAMANHO_LOTE = 20;

    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final TransmissaoDocumentoService transmissaoService;

    public TransmissaoDocumentoJob(DocumentoFiscalRepository documentoFiscalRepository,
                                   TransmissaoDocumentoService transmissaoService) {
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.transmissaoService = transmissaoService;
    }

    @Scheduled(cron = "${emissao.transmissao.cron}")
    public void executar() {
        // TRANSMITIDO primeiro: retomada de quem já está na SEFAZ (recibo, timeout, crash antes do envio)
        for (UUID id : buscar(StatusDocumentoFiscal.TRANSMITIDO)) {
            processar(id);
        }
        for (UUID id : buscar(StatusDocumentoFiscal.ASSINADO)) {
            try {
                if (transmissaoService.iniciar(id)) {
                    processar(id);
                }
            } catch (Exception falha) {
                log.error("Não foi possível iniciar a transmissão do documento {}", id, falha);
            }
        }
    }

    private List<UUID> buscar(StatusDocumentoFiscal status) {
        return documentoFiscalRepository.buscarIdsProntosPorStatus(status, OffsetDateTime.now(), PageRequest.of(0, TAMANHO_LOTE));
    }

    private void processar(UUID id) {
        try {
            transmissaoService.processar(id);
        } catch (Exception falha) {
            log.error("Falha inesperada ao processar a transmissão do documento {}", id, falha);
        }
    }
}
