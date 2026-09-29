package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.DocumentoFiscalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Varre documentos em {@code RASCUNHO} e chama a assinatura — passo assíncrono depois do {@code 202}
 * (spec §3 item 10). Mesma forma do {@code OutboxPublisherJob}: poll curto, trava por documento com
 * {@code FOR UPDATE SKIP LOCKED}, então mais de uma instância pode rodar sem assinar duas vezes.
 *
 * <p>Não é {@code @Transactional}: cada documento roda na própria transação do
 * {@link AssinaturaDocumentoService}, de modo que a falha de um não desfaz nem trava os demais. Quando a
 * assinatura falha, o {@link FalhaAssinaturaService} decide, em outra transação, entre retentar com
 * backoff e encerrar em {@code ERRO} avisando o responsável técnico — o job nunca tenta para sempre.</p>
 */
@Component
public class AssinaturaDocumentoJob {

    private static final Logger log = LoggerFactory.getLogger(AssinaturaDocumentoJob.class);
    private static final int TAMANHO_LOTE = 20;

    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final AssinaturaDocumentoService assinaturaDocumentoService;
    private final FalhaAssinaturaService falhaAssinaturaService;

    public AssinaturaDocumentoJob(DocumentoFiscalRepository documentoFiscalRepository,
                                  AssinaturaDocumentoService assinaturaDocumentoService,
                                  FalhaAssinaturaService falhaAssinaturaService) {
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.assinaturaDocumentoService = assinaturaDocumentoService;
        this.falhaAssinaturaService = falhaAssinaturaService;
    }

    @Scheduled(cron = "${emissao.assinatura.cron}")
    public void executar() {
        for (UUID id : documentoFiscalRepository.buscarIdsProntosPorStatus(
                StatusDocumentoFiscal.RASCUNHO, OffsetDateTime.now(), PageRequest.of(0, TAMANHO_LOTE))) {
            try {
                assinaturaDocumentoService.assinarSeRascunho(id);
            } catch (Exception falha) {
                registrarFalha(id, falha);
            }
        }
    }

    private void registrarFalha(UUID id, Exception falha) {
        try {
            falhaAssinaturaService.registrar(id, falha);
        } catch (Exception erroAoRegistrar) {
            // Se nem o registro da falha funciona (ex. banco fora), o documento segue em RASCUNHO e o
            // próximo ciclo tenta de novo — mas isso é falha de sistema e tem que aparecer no log.
            log.error("Não foi possível registrar a falha de assinatura do documento {}", id, erroAoRegistrar);
        }
    }
}
